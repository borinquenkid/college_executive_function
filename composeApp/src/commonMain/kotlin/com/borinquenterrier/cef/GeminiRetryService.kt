package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        private var globalHoldUntil: Long = 0L
        private var isHoldCancelled = false

        private val _globalHoldState = MutableStateFlow<Long?>(null)
        val globalHoldState: StateFlow<Long?> = _globalHoldState.asStateFlow()

        private var activeDeferred: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        var skipLongDelaysInTests = false

        fun clearRateLimitResetForTesting() {
            rateLimitResetTime = 0L
        }

        fun clearGlobalHoldForTesting() {
            globalHoldUntil = 0L
            isHoldCancelled = false
            activeDeferred = null
            _globalHoldState.value = null
            skipLongDelaysInTests = false
        }

        fun cancelHold() {
            isHoldCancelled = true
            globalHoldUntil = 0L
            _globalHoldState.value = null
            activeDeferred?.completeExceptionally(kotlinx.coroutines.CancellationException("Rate limit hold cancelled by user"))
            activeDeferred = null
        }
    }

    /**
     * Activate global hold window. Add 2 seconds grace period to wait time.
     */
    fun activateGlobalHold(delayMs: Long) {
        val until = Clock.System.now().toEpochMilliseconds() + delayMs + 2000L
        globalHoldUntil = until
        isHoldCancelled = false
        _globalHoldState.value = until
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
        if (now < globalHoldUntil) {
            val remainingSeconds = ((globalHoldUntil - now) + 999L) / 1000L
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
        if (isHoldCancelled) {
            isHoldCancelled = false
            throw kotlinx.coroutines.CancellationException("Rate limit hold cancelled by user")
        }
        if (skipLongDelaysInTests && delayMs >= 5000L) {
            throw Exception("QuotaExhausted: Rate limit delay of $delayMs ms is too long for integration tests.")
        }

        val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
        activeDeferred = deferred

        try {
            kotlinx.coroutines.coroutineScope {
                val delayJob = launch {
                    try {
                        delayFn(delayMs)
                        deferred.complete(Unit)
                    } catch (e: Exception) {
                        deferred.completeExceptionally(e)
                    }
                }
                
                try {
                    deferred.await()
                } finally {
                    delayJob.cancel()
                }
            }
        } finally {
            if (activeDeferred === deferred) {
                activeDeferred = null
            }
            val now = Clock.System.now().toEpochMilliseconds()
            if (now >= globalHoldUntil) {
                _globalHoldState.value = null
            }
        }
    }
}
