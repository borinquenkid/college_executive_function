package com.borinquenterrier.cef

/**
 * Per-model context-window sizes (design 2.1, Part B) — the missing primitive prompt budgeting
 * needs. Mirrors [ModelRpm]'s exact-then-longest-prefix-then-default lookup so both tables read
 * the same way.
 *
 * Values are the published Gemini input-token ceilings, kept conservative. An unrecognized model
 * id falls back to [DEFAULT_CONTEXT_WINDOW] — deliberately small, since underestimating a real
 * window only forces an unneeded compaction, while overestimating an unknown model's window risks
 * an oversized-request failure.
 */
object ModelContextWindow {
    /** Conservative fallback for any model we don't recognise. */
    const val DEFAULT_CONTEXT_WINDOW = 32_000

    // Published Gemini input-token limits. Tune here.
    private val windowByModel: Map<String, Int> = mapOf(
        "gemini-2.5-pro" to 1_048_576,
        "gemini-2.5-flash" to 1_048_576,
        "gemini-2.5-flash-lite" to 1_048_576,
        "gemini-flash-latest" to 1_048_576,
        "gemini-flash-lite-latest" to 1_048_576,
        "gemini-3.5-flash" to 1_048_576,
        "gemini-2.0-flash" to 1_048_576,
        "gemini-2.0-flash-lite" to 1_048_576,
    )

    /**
     * Context window for [modelName]: exact match first, then the longest known prefix (so a
     * preview/versioned id inherits its base family's window), then [DEFAULT_CONTEXT_WINDOW].
     */
    fun contextWindowFor(modelName: String): Int {
        windowByModel[modelName]?.let { return it }
        return windowByModel.entries
            .filter { modelName.startsWith(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
            ?: DEFAULT_CONTEXT_WINDOW
    }

    /**
     * Conservative window for the chat path. Chat always negotiates [GeminiModelNegotiator]'s
     * `LIGHT` tier, but which model in that tier actually serves a given call is only decided at
     * request time (availability/rate-limit dependent). Budgeting against the smallest window in
     * the tier avoids guessing wrong and building an oversized prompt.
     */
    fun conservativeChatWindow(): Int =
        GeminiModelNegotiator.LIGHT_PREFERENCES.minOf { contextWindowFor(it) }
}
