package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

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

    companion object {
        private var rateLimitResetTime: Long = 0L

        fun clearRateLimitResetForTesting() {
            rateLimitResetTime = 0L
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
        val now = Clock.System.now().toEpochMilliseconds()
        if (now < rateLimitResetTime) {
            val remainingSeconds = ((rateLimitResetTime - now) + 999L) / 1000L
            throw Exception("QuotaExhausted: Rate limit reached. Please wait $remainingSeconds seconds before trying again.")
        }

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

                    val delayMs: Long = resolveRetryDelay(
                        status = httpResponse.status,
                        headers = httpResponse.headers,
                        body = responseBody,
                        attempts = attempts,
                        tag = tag
                    )

                    if (delayMs > 10000L) {
                        modelNegotiator.blacklistModel(modelName)
                        modelNegotiator.evictFromCache(modelName)
                        logger?.e(tag, "⚠️ Model $modelName returned rate limit delay of $delayMs ms. Blacklisted and evicted from cache. Trying next model...")
                        lastError = Exception("QuotaExhausted: Rate limit reached for model $modelName. Delay: ${delayMs / 1000}s.")
                        continue
                    }

                    delayFn(delayMs)
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
                delayFn(delayMs)
            }
        }

        if (lastNegotiatedModel != null) {
            modelNegotiator.blacklistModel(lastNegotiatedModel)
            modelNegotiator.evictFromCache(lastNegotiatedModel)
            logger?.e(tag, "⚠️ Model $lastNegotiatedModel failed all $maxAttempts retry attempts. Evicted from cache and blacklisted.")
        }

        val errorToThrow = lastError ?: Exception("Failed after $maxAttempts attempts")
        if (errorToThrow.message?.contains("QuotaExhausted", ignoreCase = true) == true) {
            rateLimitResetTime = Clock.System.now().toEpochMilliseconds() + (5 * 60 * 1000L) // 5 minutes block
        }
        throw errorToThrow
    }

    internal fun resolveRetryDelay(
        status: HttpStatusCode,
        headers: io.ktor.http.Headers,
        body: String,
        attempts: Int,
        tag: String
    ): Long {
        val bodyRetryMatch = Regex("""retry in (\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)
            .find(body)
        if (bodyRetryMatch != null) {
            val seconds = bodyRetryMatch.groupValues[1].toDoubleOrNull()
            if (seconds != null) {
                val ms = (seconds * 1000).toLong() + 500L
                logger?.d(tag, "⏱️ Rate-limited — server body says retry in ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        val resetHeader = headers["x-ratelimit-reset"] ?: headers["X-RateLimit-Reset"]
        if (resetHeader != null) {
            val resetEpoch = resetHeader.toLongOrNull()
            if (resetEpoch != null) {
                val nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000L
                val waitSeconds = (resetEpoch - nowSeconds).coerceAtLeast(1L)
                val ms = waitSeconds * 1000L + 500L
                logger?.d(tag, "⏱️ Rate-limited — x-ratelimit-reset in ${waitSeconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        val retryAfter = headers["Retry-After"] ?: headers["retry-after"]
        if (retryAfter != null) {
            val seconds = retryAfter.toLongOrNull()
            if (seconds != null) {
                val ms = seconds * 1000L
                logger?.d(tag, "⏱️ Rate-limited — Retry-After: ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        val baseDelay = if (status == HttpStatusCode.TooManyRequests) 2000L else 1000L
        val ms = baseDelay * (1 shl (attempts - 1))
        logger?.d(tag, "⚠️ Transient error ($status). No server hint — exponential backoff ${ms}ms (attempt $attempts).")
        return ms
    }
}
