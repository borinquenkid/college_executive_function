package com.borinquenterrier.cef

/**
 * Detects whether a 429 Too Many Requests response indicates quota exhaustion
 * vs. transient rate limiting.
 *
 * Quota exhaustion has specific signal keywords (quota + exhausted/exceeded/limit)
 * without a transient "retry in N seconds" message.
 */
class QuotaExhaustionDetector {

    fun isQuotaExhausted(errorBody: String): Boolean {
        val hasRetryHint = errorBody.contains("retry in", ignoreCase = true)
        val hasQuotaWord = errorBody.contains("quota", ignoreCase = true)
        val hasExhaustionWord = errorBody.contains("exhausted", ignoreCase = true) ||
                errorBody.contains("exceeded", ignoreCase = true) ||
                errorBody.contains("limit", ignoreCase = true)
        return !hasRetryHint && hasQuotaWord && hasExhaustionWord
    }
}
