package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Handles HTTP request execution, retry policies, backoff delays, and rate-limiting resolution for Gemini models.
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
    private val retryService = GeminiRetryService(logger, delayFn)

    companion object {
        fun clearRateLimitResetForTesting() {
            GeminiRetryService.clearRateLimitResetForTesting()
        }
    }

    suspend fun postToModel(modelName: String, body: JsonObject): HttpResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        val authUrl = if (apiKey != null) "$url?key=$apiKey" else url
        return client.post(authUrl) {
            contentType(ContentType.Application.Json)
            if (apiKey == null && accessToken != null) {
                header("Authorization", "Bearer $accessToken")
            }
            setBody(body)
        }
    }

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

        while (attempts < maxAttempts) {
            val modelName = modelNegotiator.negotiateBestModel(available, tier)
            lastNegotiatedModel = modelName
            try {
                val httpResponse = postToModel(modelName, body(modelName))
                val responseBody = httpResponse.bodyAsText()

                // Fatal errors — throw immediately
                if (httpResponse.status == HttpStatusCode.Unauthorized) {
                    logger?.e(tag, "401 Unauthorized: Your API Key or Access Token is invalid/expired.")
                    throw Exception("Unauthorized")
                }
                if (httpResponse.status == HttpStatusCode.Forbidden) {
                    logger?.e(tag, "403 Forbidden: Ensure the Gemini API is enabled in your Google Cloud Project.")
                    throw Exception("Forbidden")
                }

                // Structural errors — blacklist model, try next
                if (httpResponse.status == HttpStatusCode.NotFound) {
                    modelNegotiator.blacklistModel(modelName)
                    modelNegotiator.evictFromCache(modelName)
                    logger?.d(tag, "⚠️ Model $modelName returned 404 (Not Found). Blacklisted. Trying next model...")
                    attempts++
                    continue
                }
                if (httpResponse.status == HttpStatusCode.BadRequest && responseBody.contains("response modalities")) {
                    modelNegotiator.blacklistModel(modelName)
                    modelNegotiator.evictFromCache(modelName)
                    logger?.d(tag, "⚠️ Model $modelName does not support text responses. Blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                // Quota exhaustion (RPD)
                if (httpResponse.status == HttpStatusCode.TooManyRequests) {
                    val hasRetryHint = responseBody.contains("retry in", ignoreCase = true)
                    val hasQuotaWord = responseBody.contains("quota", ignoreCase = true)
                    val hasExhaustionWord = responseBody.contains("exhausted", ignoreCase = true) ||
                        responseBody.contains("exceeded", ignoreCase = true) ||
                        responseBody.contains("limit", ignoreCase = true)
                    val isQuotaExhausted = !hasRetryHint && hasQuotaWord && hasExhaustionWord
                    if (isQuotaExhausted) {
                        logger?.e(tag, "🚫 Daily quota exhausted for model $modelName. Blacklisting and trying next model...")
                        telemetryManager?.logRateLimitError()
                        modelNegotiator.blacklistModel(modelName)
                        modelNegotiator.evictFromCache(modelName)
                        lastError = Exception("QuotaExhausted: Daily request limit reached for model $modelName.")
                        attempts++
                        continue
                    }
                }

                // Transient 5xx / 503 errors
                if (httpResponse.status == HttpStatusCode.ServiceUnavailable ||
                    httpResponse.status.value >= 500
                ) {
                    telemetryManager?.logRateLimitError()
                    modelNegotiator.blacklistModel(modelName)
                    modelNegotiator.evictFromCache(modelName)
                    logger?.e(tag, "⚠️ Model $modelName returned ${httpResponse.status}. Evicted from cache and blacklisted. Trying next model...")
                    attempts++
                    continue
                }

                // Transient 429 Too Many Requests
                if (httpResponse.status == HttpStatusCode.TooManyRequests) {
                    telemetryManager?.logRateLimitError()
                    attempts++

                    val delayMs: Long = retryService.resolveRetryDelay(
                        status = httpResponse.status,
                        headers = httpResponse.headers,
                        body = responseBody,
                        attempts = attempts
                    )

                    if (delayMs > 10000L) {
                        modelNegotiator.blacklistModel(modelName)
                        modelNegotiator.evictFromCache(modelName)
                        logger?.e(tag, "⚠️ Model $modelName returned rate limit delay of $delayMs ms. Blacklisted and evicted from cache. Trying next model...")
                        lastError = Exception("QuotaExhausted: Rate limit reached for model $modelName. Delay: ${delayMs / 1000}s.")
                        continue
                    }

                    retryService.wait(delayMs)
                    continue
                }

                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
                }

                // Success
                val geminiResponse = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(responseBody)
                val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response from AI")

                return parseResponse(responseText)

            } catch (e: Exception) {
                if (e.message == "Unauthorized" || 
                    e.message == "Forbidden" || 
                    e.message?.startsWith("QuotaExhausted") == true
                ) throw e

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
            logger?.e(tag, "⚠️ Model $lastNegotiatedModel failed all $maxAttempts retry attempts. Evicted from cache and blacklisted.")
        }

        val errorToThrow = lastError ?: Exception("Failed after $maxAttempts attempts")
        if (errorToThrow.message?.contains("QuotaExhausted", ignoreCase = true) == true) {
            retryService.activateRateLimitWindow()
        }
        throw errorToThrow
    }
}
