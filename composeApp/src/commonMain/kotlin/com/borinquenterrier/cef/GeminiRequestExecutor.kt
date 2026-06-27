package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Lightweight facade orchestrating Gemini API request execution with retry logic.
 * Delegates to specialized services:
 * - GeminiRequestBuilder: HTTP request construction and execution
 * - GeminiErrorHandler: Error categorization and handling
 * - GeminiRetryService: Retry delays and rate-limit windows
 */
class GeminiRequestExecutor(
    private val client: HttpClient,
    private val apiKey: String?,
    private val accessToken: String?,
    private val logger: Logger?,
    private val telemetryManager: TelemetryManager?,
    private val modelNegotiator: GeminiModelNegotiator,
    private val delayFn: suspend (Long) -> Unit
) {
    private val tag = "GeminiAI"
    private val requestBuilder = GeminiRequestBuilder(client, apiKey, accessToken)
    private val errorHandler = GeminiErrorHandler(
        ErrorCategorizer(QuotaExhaustionDetector(), RetryAfterParser(), logger),
        modelNegotiator,
        logger
    )
    private val retryService = GeminiRetryService(logger, delayFn)

    companion object {
        private val queue = GeminiRequestQueue.shared()

        fun clearRateLimitResetForTesting() {
            GeminiRetryService.clearRateLimitResetForTesting()
            GeminiRetryService.clearGlobalHoldForTesting()
            queue.isBypassed = true
        }
    }

    suspend fun postToModel(modelName: String, body: JsonObject) =
        requestBuilder.postToModel(modelName, body)

    suspend fun <T> executeWithRetry(
        maxAttempts: Int,
        tier: GeminiAIService.TaskTier,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T = queue.enqueue { executeWithRetryInternal(maxAttempts, tier, body, parseResponse) }

    private suspend fun <T> executeWithRetryInternal(
        maxAttempts: Int,
        tier: GeminiAIService.TaskTier,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T {
        AppTracer.current.event(
            "gemini.preflight",
            mapOf(
                "global_hold_active" to GeminiRetryService.isGlobalHoldActive().toString(),
                "rate_limit_window_active" to GeminiRetryService.isRateLimitWindowActive().toString()
            )
        )
        retryService.checkRateLimitWindow()

        val available = modelNegotiator.getAvailableModels()
        var attempts = 0
        var lastError: Exception? = null
        var lastNegotiatedModel = ""
        var consecutiveRateLimitCount = 0
        var consecutiveExtremeCount = 0

        while (attempts < maxAttempts) {
            val modelName = modelNegotiator.negotiateBestModel(available, tier)
            lastNegotiatedModel = modelName
            try {
                val (httpResponse, responseBody) = AppTracer.current.span(
                    "gemini.http_request",
                    mapOf("model" to modelName, "attempt" to (attempts + 1).toString())
                ) {
                    val response = postToModel(modelName, body(modelName))
                    val body = response.bodyAsText()
                    setAttribute("http.status", response.status.value.toLong())
                    setAttribute("response.bytes", body.length.toLong())
                    Pair(response, body)
                }

                if (httpResponse.status.isSuccess()) {
                    consecutiveRateLimitCount = 0
                    val geminiResponse =
                        Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(responseBody)
                    val candidate = geminiResponse.candidates.firstOrNull()
                        ?: throw Exception("Empty response from AI")
                    val parts = candidate.content.parts
                    val responseText = if (parts.isNotEmpty()) parts[0].text
                        else throw Exception("Empty response from AI")
                    return parseResponse(responseText)
                }

                val errorType = errorHandler.categorizeError(httpResponse.status, responseBody)

                if (errorType == ErrorCategorizer.ErrorType.Unauthorized) {
                    logger?.e(tag, "401 Unauthorized: Your API Key or Access Token is invalid/expired.")
                    throw Exception("Unauthorized")
                }
                if (errorType == ErrorCategorizer.ErrorType.Forbidden) {
                    logger?.e(tag, "403 Forbidden: Ensure the Gemini API is enabled in your Google Cloud Project.")
                    throw Exception("Forbidden")
                }

                if (errorType is ErrorCategorizer.ErrorType.StructuralError) {
                    errorHandler.handleStructuralError(modelName)
                    logger?.d(tag, "⚠️ Model $modelName had structural error: ${errorType.reason}. Blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                if (errorType == ErrorCategorizer.ErrorType.QuotaExhausted) {
                    telemetryManager?.logRateLimitError()
                    errorHandler.handleServerError(modelName)
                    logger?.e(tag, "🚫 Daily quota exhausted for model $modelName. Blacklisting and trying next model...")
                    lastError = Exception("QuotaExhausted: Daily request limit reached for model $modelName.")
                    attempts++
                    continue
                }

                if (errorType == ErrorCategorizer.ErrorType.TransientServerError) {
                    telemetryManager?.logRateLimitError()
                    errorHandler.handleServerError(modelName)
                    logger?.e(tag, "⚠️ Model $modelName returned server error. Evicted and blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                if (errorType is ErrorCategorizer.ErrorType.TransientRateLimit) {
                    telemetryManager?.logRateLimitError()
                    val decision = GeminiRateLimitPolicy.decide(
                        delayMs = errorType.delayMs,
                        consecutiveExtremeCount = consecutiveExtremeCount,
                        consecutiveRateLimitCount = consecutiveRateLimitCount,
                        modelName = modelName
                    )
                    when (decision) {
                        is GeminiRateLimitPolicy.Decision.ExtremeDelay -> {
                            modelNegotiator.blacklistModel(modelName)
                            modelNegotiator.evictFromCache(modelName)
                            consecutiveExtremeCount++
                            logger?.e(tag, "⚠️ Model $modelName has extreme rate limit. Treating as exhausted.")
                            lastError = Exception(decision.errorMessage)
                            if (decision.advanceAttempt) { attempts++; consecutiveExtremeCount = 0 }
                        }
                        is GeminiRateLimitPolicy.Decision.SaturatedKey -> {
                            consecutiveExtremeCount = 0
                            attempts++
                            consecutiveRateLimitCount++
                            logger?.e(tag, "⚠️ API key saturated. Holding ${decision.holdDelayMs}ms.")
                            modelNegotiator.blacklistModel(modelName, decision.blacklistDurationMs)
                            modelNegotiator.evictFromCache(modelName)
                            retryService.activateGlobalHold(decision.holdDelayMs)
                            retryService.wait(decision.holdDelayMs)
                            consecutiveRateLimitCount = 0
                        }
                        is GeminiRateLimitPolicy.Decision.LongDelay -> {
                            consecutiveExtremeCount = 0
                            attempts++
                            consecutiveRateLimitCount++
                            modelNegotiator.blacklistModel(modelName, decision.blacklistDurationMs)
                            modelNegotiator.evictFromCache(modelName)
                            logger?.e(tag, "⚠️ Model $modelName rate limit too long. Blacklisted.")
                            lastError = Exception(decision.errorMessage)
                        }
                        is GeminiRateLimitPolicy.Decision.ShortDelay -> {
                            consecutiveExtremeCount = 0
                            consecutiveRateLimitCount = 0
                            attempts++
                            retryService.wait(decision.delayMs)
                        }
                    }
                    continue
                }

                // sealed class: all other ErrorType variants handled above; errorType is always OtherError here
                val otherError = errorType as ErrorCategorizer.ErrorType.OtherError
                logger?.e(tag, "API Error: ${otherError.message}")
                throw Exception(otherError.message)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = e.message.orEmpty()
                if (msg.contains("Unauthorized") || msg.contains("Forbidden") || msg.contains("QuotaExhausted")) throw e

                lastError = e
                val safeMsg = e.message?.replace(Regex("key=[A-Za-z0-9_-]+"), "key=[REDACTED]") ?: ""
                logger?.e(tag, "Attempt ${attempts + 1} failed: $safeMsg")
                attempts++
                val delayMs = 1000L * (1 shl (attempts - 1))
                retryService.wait(delayMs)
            }
        }

        val isNetworkError = lastError?.message?.let { msg ->
            msg.contains("Socket timeout", ignoreCase = true) ||
            msg.contains("ConnectException", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ||
            msg.contains("Failed to connect", ignoreCase = true)
        } ?: false

        if (isNetworkError) {
            logger?.e(tag, "⚠️ Model $lastNegotiatedModel failed all $maxAttempts attempts due to network error. NOT blacklisted.")
        } else {
            modelNegotiator.blacklistModel(lastNegotiatedModel)
            modelNegotiator.evictFromCache(lastNegotiatedModel)
            logger?.e(tag, "⚠️ Model $lastNegotiatedModel failed all $maxAttempts attempts. Evicted and blacklisted.")
        }

        val errorToThrow = lastError ?: Exception("Failed after $maxAttempts attempts")
        if (errorToThrow.message.orEmpty().contains("QuotaExhausted", ignoreCase = true)) {
            retryService.activateRateLimitWindow()
        }
        throw errorToThrow
    }
}

