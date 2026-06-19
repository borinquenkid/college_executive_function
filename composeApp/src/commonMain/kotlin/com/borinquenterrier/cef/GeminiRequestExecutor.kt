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
        maxAttempts: Int = 5,
        tier: GeminiAIService.TaskTier = GeminiAIService.TaskTier.HEAVY,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T = queue.enqueue { executeWithRetryInternal(maxAttempts, tier, body, parseResponse) }

    private suspend fun <T> executeWithRetryInternal(
        maxAttempts: Int,
        tier: GeminiAIService.TaskTier,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T {
        retryService.checkRateLimitWindow()

        val available = modelNegotiator.getAvailableModels()
        var attempts = 0
        var lastError: Exception? = null
        var lastNegotiatedModel: String? = null
        var consecutiveRateLimitCount = 0
        var consecutiveExtremeCount = 0

        while (attempts < maxAttempts) {
            val modelName = modelNegotiator.negotiateBestModel(available, tier)
            lastNegotiatedModel = modelName
            try {
                val httpResponse = postToModel(modelName, body(modelName))
                val responseBody = httpResponse.bodyAsText()

                if (httpResponse.status.isSuccess()) {
                    consecutiveRateLimitCount = 0
                    val geminiResponse =
                        Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(responseBody)
                    val responseText =
                        geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: throw Exception("Empty response from AI")
                    return parseResponse(responseText)
                }

                val errorType = errorHandler.categorizeError(httpResponse.status, responseBody)

                if (errorType == ErrorCategorizer.ErrorType.Unauthorized ||
                    errorType == ErrorCategorizer.ErrorType.Forbidden
                ) {
                    val msg = when (errorType) {
                        ErrorCategorizer.ErrorType.Unauthorized -> "401 Unauthorized: Your API Key or Access Token is invalid/expired."
                        ErrorCategorizer.ErrorType.Forbidden -> "403 Forbidden: Ensure the Gemini API is enabled in your Google Cloud Project."
                        else -> "Unknown auth error"
                    }
                    logger?.e(tag, msg)
                    throw Exception(
                        when (errorType) {
                            ErrorCategorizer.ErrorType.Unauthorized -> "Unauthorized"
                            ErrorCategorizer.ErrorType.Forbidden -> "Forbidden"
                            else -> "UnknownAuthError"
                        }
                    )
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

                if (errorType is ErrorCategorizer.ErrorType.OtherError) {
                    val fullMessage = if (errorType.message.isNotBlank()) errorType.message
                    else "Unknown error. Response: $responseBody"
                    logger?.e(tag, "API Error: $fullMessage")
                    throw Exception(fullMessage)
                }

                throw Exception("Unexpected response status: ${httpResponse.status}")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e.message?.let {
                        it.contains("Unauthorized") || it.contains("Forbidden") || it.contains("QuotaExhausted")
                    } == true) throw e

                lastError = e
                logger?.e(tag, "Attempt ${attempts + 1} failed: ${e.message}")
                attempts++
                val delayMs = 1000L * (1 shl (attempts - 1))
                retryService.wait(delayMs)
            }
        }

        if (lastNegotiatedModel != null) {
            modelNegotiator.blacklistModel(lastNegotiatedModel)
            modelNegotiator.evictFromCache(lastNegotiatedModel)
            logger?.e(tag, "⚠️ Model $lastNegotiatedModel failed all $maxAttempts attempts. Evicted and blacklisted.")
        }

        val errorToThrow = lastError ?: Exception("Failed after $maxAttempts attempts")
        if (errorToThrow.message?.contains("QuotaExhausted", ignoreCase = true) == true) {
            retryService.activateRateLimitWindow()
        }
        throw errorToThrow
    }
}

