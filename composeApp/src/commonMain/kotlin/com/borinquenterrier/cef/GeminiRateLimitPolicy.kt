package com.borinquenterrier.cef

/**
 * Pure decision function for TransientRateLimit handling in GeminiRequestExecutor.
 * Translates (delayMs, consecutiveExtremeCount, consecutiveRateLimitCount) into a
 * declarative [Decision] that executeWithRetryInternal applies — keeping branch logic
 * out of the retry loop and making it independently testable.
 */
internal object GeminiRateLimitPolicy {

    sealed class Decision {
        /** Extreme delay (>120s): blacklist, track extreme count, maybe advance attempt. */
        data class ExtremeDelay(
            val blacklistModel: Boolean = true,
            val advanceAttempt: Boolean,
            val errorMessage: String
        ) : Decision()

        /** Long delay (>10s) with saturated key: blacklist + activate global hold + wait. */
        data class SaturatedKey(
            val holdDelayMs: Long,
            val blacklistDurationMs: Long
        ) : Decision()

        /** Long delay (>10s), first occurrence: blacklist model, skip wait. */
        data class LongDelay(
            val blacklistDurationMs: Long,
            val errorMessage: String
        ) : Decision()

        /** Short delay (≤10s): just wait. */
        data class ShortDelay(val delayMs: Long) : Decision()
    }

    fun decide(
        delayMs: Long,
        consecutiveExtremeCount: Int,
        consecutiveRateLimitCount: Int,
        modelName: String
    ): Decision {
        if (delayMs > 120_000L) {
            val newExtremeCount = consecutiveExtremeCount + 1
            return Decision.ExtremeDelay(
                advanceAttempt = newExtremeCount >= 10,
                errorMessage = "RateLimited: Extreme rate limit for model $modelName. Delay: ${delayMs / 1000}s."
            )
        }

        if (delayMs > 10_000L) {
            val newRateLimitCount = consecutiveRateLimitCount + 1
            return if (newRateLimitCount >= 2) {
                Decision.SaturatedKey(
                    holdDelayMs = delayMs,
                    blacklistDurationMs = delayMs + 2000L
                )
            } else {
                Decision.LongDelay(
                    blacklistDurationMs = delayMs + 2000L,
                    errorMessage = "RateLimited: Temporary rate limit for model $modelName. Delay: ${delayMs / 1000}s."
                )
            }
        }

        return Decision.ShortDelay(delayMs)
    }
}
