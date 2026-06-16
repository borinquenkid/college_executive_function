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
        fun clearRateLimitResetForTesting() {
            GeminiRetryService.clearRateLimitResetForTesting()
            GeminiRetryService.clearGlobalHoldForTesting()
        }
    }

    suspend fun postToModel(modelName: String, body: JsonObject) =
        requestBuilder.postToModel(modelName, body)

    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 5,
        tier: GeminiAIService.TaskTier = GeminiAIService.TaskTier.HEAVY,
        body: (modelName: String) -> JsonObject,
        parseResponse: (responseText: String) -> T
    ): T {
        retryService.checkRateLimitWindow()

        val available = modelNegotiator.getAvailableModels()
        var attempts = 0
        var lastError: Exception? = null
        var lastNegotiatedModel: String? = null
        var consecutiveRateLimitCount = 0

        while (attempts < maxAttempts) {
            val modelName = modelNegotiator.negotiateBestModel(available, tier)
            lastNegotiatedModel = modelName
            try {
                val httpResponse = postToModel(modelName, body(modelName))
                val responseBody = httpResponse.bodyAsText()

                // Handle success immediately (2xx status codes)
                if (httpResponse.status.isSuccess()) {
                    consecutiveRateLimitCount = 0
                    val geminiResponse =
                        Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(
                            responseBody
                        )
                    val responseText =
                        geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: throw Exception("Empty response from AI")
                    return parseResponse(responseText)
                }

                // Categorize error (only for non-2xx responses)
                val errorType = errorHandler.categorizeError(httpResponse.status, responseBody)

                // Handle fatal errors
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

                // Handle structural errors (model not found, unsupported modalities)
                if (errorType is ErrorCategorizer.ErrorType.StructuralError) {
                    errorHandler.handleStructuralError(modelName)
                    logger?.d(
                        tag,
                        "⚠️ Model $modelName had structural error: ${errorType.reason}. Blacklisted. Trying next model..."
                    )
                    attempts++
                    continue
                }

                // Handle quota exhaustion
                if (errorType == ErrorCategorizer.ErrorType.QuotaExhausted) {
                    telemetryManager?.logRateLimitError()
                    errorHandler.handleServerError(modelName)
                    logger?.e(
                        tag,
                        "🚫 Daily quota exhausted for model $modelName. Blacklisting and trying next model..."
                    )
                    lastError =
                        Exception("QuotaExhausted: Daily request limit reached for model $modelName.")
                    attempts++
                    continue
                }

                // Handle server errors (5xx)
                if (errorType == ErrorCategorizer.ErrorType.TransientServerError) {
                    telemetryManager?.logRateLimitError()
                    errorHandler.handleServerError(modelName)
                    logger?.e(
                        tag,
                        "⚠️ Model $modelName returned server error. Evicted and blacklisted. Trying next model..."
                    )
                    attempts++
                    continue
                }

                // Handle transient rate limits (429)
                if (errorType is ErrorCategorizer.ErrorType.TransientRateLimit) {
                    telemetryManager?.logRateLimitError()
                    attempts++

                    val delayMs = errorType.delayMs
                    if (delayMs > 10000L) {
                        consecutiveRateLimitCount++
                        if (consecutiveRateLimitCount >= 2) {
                            logger?.e(tag, "⚠️ API key saturated (${consecutiveRateLimitCount} consecutive long rate limits). Holding ${delayMs}ms before retry.")
                            retryService.activateGlobalHold(delayMs)
                            retryService.wait(delayMs)
                            consecutiveRateLimitCount = 0
                            continue
                        }
                        modelNegotiator.blacklistModel(modelName)
                        modelNegotiator.evictFromCache(modelName)
                        logger?.e(
                            tag,
                            "⚠️ Model $modelName rate limit delay ${delayMs}ms too long. Blacklisted. Trying next model..."
                        )
                        lastError =
                            Exception("QuotaExhausted: Rate limit reached for model $modelName. Delay: ${delayMs / 1000}s.")
                        continue
                    }

                    consecutiveRateLimitCount = 0
                    retryService.wait(delayMs)
                    continue
                }

                // Handle other errors (should not reach here for 2xx responses)
                if (errorType is ErrorCategorizer.ErrorType.OtherError) {
                    val fullMessage = if (errorType.message.isNotBlank()) {
                        errorType.message
                    } else {
                        "Unknown error. Response: $responseBody"
                    }
                    logger?.e(tag, "API Error: $fullMessage")
                    throw Exception(fullMessage)
                }

                // Defensive: throw if we somehow reach here
                throw Exception("Unexpected response status: ${httpResponse.status}")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                if (e.message?.let {
                        it.contains("Unauthorized") || it.contains("Forbidden") || it.contains(
                            "QuotaExhausted"
                        )
                    } == true) {
                    throw e
                }

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
            logger?.e(
                tag,
                "⚠️ Model $lastNegotiatedModel failed all $maxAttempts attempts. Evicted and blacklisted."
            )
        }

        val errorToThrow = lastError ?: Exception("Failed after $maxAttempts attempts")
        if (errorToThrow.message?.contains("QuotaExhausted", ignoreCase = true) == true) {
            retryService.activateRateLimitWindow()
        }
        throw errorToThrow
    }
}

