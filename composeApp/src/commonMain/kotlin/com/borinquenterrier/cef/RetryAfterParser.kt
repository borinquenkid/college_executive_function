package com.borinquenterrier.cef

/**
 * Extracts the "retry after" delay from Gemini API 429 responses.
 * Parses both standard HTTP Retry-After headers and inline message text.
 */
class RetryAfterParser {
    private val retryAfterRegex = Regex("""retry[^0-9]*(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Extract retry delay in milliseconds from error response body.
     * Falls back to 1000ms if parsing fails.
     *
     * @param errorBody Error message body containing retry hint
     * @return Delay in milliseconds
     */
    fun extractRetryAfterMs(errorBody: String): Long {
        val match = retryAfterRegex.find(errorBody)
        return if (match != null) {
            match.groupValues[1].toLongOrNull()?.let { it * 1000 } ?: 1000L
        } else {
            1000L
        }
    }
}
