package com.borinquenterrier.cef

/**
 * Cheap, local token-count heuristic for prompt budgeting (design 2.1, Part B). Uses a
 * chars/4 approximation rather than an API round-trip; the seam here is deliberate so a real
 * `countTokens` call can replace [estimate]'s body later without touching call sites.
 */
object TokenEstimator {
    private const val CHARS_PER_TOKEN = 4

    /** Rounds up so a non-empty string never estimates to zero tokens. */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
    }
}
