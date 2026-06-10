package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock

/**
 * Encapsulates retry delay calculation, rate limit tracking, and backoff strategy.
 * Reduces cyclomatic complexity in GeminiRequestExecutor.
 */
class GeminiRetryService(
    private val logger: Logger?,
    private val delayFn: suspend (Long) -> Unit
) {
    private val tag = "GeminiRetry"

    companion object {
        private var rateLimitResetTime: Long = 0L

        fun clearRateLimitResetForTesting() {
            rateLimitResetTime = 0L
        }
    }

    /**
     * Check if rate limit is active. Throws if quota is exhausted.
     */
    fun checkRateLimitWindow() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now < rateLimitResetTime) {
            val remainingSeconds = ((rateLimitResetTime - now) + 999L) / 1000L
            throw Exception("QuotaExhausted: Rate limit reached. Please wait $remainingSeconds seconds before trying again.")
        }
    }

    /**
     * Resolve retry delay from various sources (body, headers, exponential backoff).
     */
    fun resolveRetryDelay(
        status: HttpStatusCode,
        headers: io.ktor.http.Headers,
        body: String,
        attempts: Int
    ): Long {
        // Try server body hint: "retry in X.X s"
        val bodyRetryMatch = Regex("""retry in (\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)
            .find(body)
        if (bodyRetryMatch != null) {
            val seconds = bodyRetryMatch.groupValues[1].toDoubleOrNull()
            if (seconds != null) {
                val ms = (seconds * 1000).toLong() + 500L
                logger?.d(
                    tag,
                    "⏱️ Rate-limited — server body says retry in ${seconds}s. Waiting ${ms}ms."
                )
                return ms
            }
        }

        // Try x-ratelimit-reset header
        val resetHeader = headers["x-ratelimit-reset"] ?: headers["X-RateLimit-Reset"]
        if (resetHeader != null) {
            val resetEpoch = resetHeader.toLongOrNull()
            if (resetEpoch != null) {
                val nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000L
                val waitSeconds = (resetEpoch - nowSeconds).coerceAtLeast(1L)
                val ms = waitSeconds * 1000L + 500L
                logger?.d(
                    tag,
                    "⏱️ Rate-limited — x-ratelimit-reset in ${waitSeconds}s. Waiting ${ms}ms."
                )
                return ms
            }
        }

        // Try Retry-After header
        val retryAfter = headers["Retry-After"] ?: headers["retry-after"]
        if (retryAfter != null) {
            val seconds = retryAfter.toLongOrNull()
            if (seconds != null) {
                val ms = seconds * 1000L
                logger?.d(tag, "⏱️ Rate-limited — Retry-After: ${seconds}s. Waiting ${ms}ms.")
                return ms
            }
        }

        // Exponential backoff fallback
        val baseDelay = if (status == HttpStatusCode.TooManyRequests) 2000L else 1000L
        val ms = baseDelay * (1 shl (attempts - 1))
        logger?.d(
            tag,
            "⚠️ Transient error ($status). No server hint — exponential backoff ${ms}ms (attempt $attempts)."
        )
        return ms
    }

    /**
     * Activate rate limit window for quota exhaustion (typically 5 minutes).
     */
    fun activateRateLimitWindow() {
        rateLimitResetTime = Clock.System.now().toEpochMilliseconds() + (5 * 60 * 1000L)
    }

    /**
     * Wait for calculated delay using injectable delay function (testable).
     */
    suspend fun wait(delayMs: Long) {
        delayFn(delayMs)
    }
}
