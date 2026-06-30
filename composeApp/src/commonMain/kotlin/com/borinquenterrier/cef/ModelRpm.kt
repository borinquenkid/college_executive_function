package com.borinquenterrier.cef

/**
 * Per-model request pacing — "pace for the model itself".
 *
 * Gemini enforces a per-model requests-per-minute (RPM) ceiling on the free tier; exceeding
 * it produces 429s and, under sustained load, hung sockets. Each [GeminiRequestQueue] paces
 * itself to the RPM of the model **actually used in its slot**, so the pipeline runs as fast
 * as that model allows and no faster — reliable even when slow.
 *
 * Values are deliberately conservative (kept at or below published free-tier ceilings for
 * headroom) and are the single place to tune pacing. Unlisted or preview model ids inherit
 * their base family's ceiling via longest-prefix match; anything truly unknown falls back to
 * [DEFAULT_RPM].
 */
object ModelRpm {
    /** Conservative pacing for any model we don't recognise. */
    const val DEFAULT_RPM = 8

    // Conservative free-tier RPM ceilings. Tune here.
    private val rpmByModel: Map<String, Int> = mapOf(
        "gemini-2.5-pro" to 5,
        "gemini-2.5-flash" to 10,
        "gemini-2.5-flash-lite" to 15,
        "gemini-flash-latest" to 10,
        "gemini-flash-lite-latest" to 15,
        "gemini-3.5-flash" to 8,
        "gemini-2.0-flash" to 15,
        "gemini-2.0-flash-lite" to 30,
    )

    /**
     * RPM ceiling for [modelName]: exact match first, then the longest known prefix (so a
     * preview/versioned id like `gemini-2.5-flash-preview-09-2025` inherits its base family's
     * ceiling), then [DEFAULT_RPM].
     */
    fun rpmFor(modelName: String): Int {
        rpmByModel[modelName]?.let { return it }
        return rpmByModel.entries
            .filter { modelName.startsWith(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
            ?: DEFAULT_RPM
    }

    /** Minimum spacing, in ms, between consecutive requests to [modelName]. */
    fun intervalMsFor(modelName: String): Long {
        val rpm = rpmFor(modelName).coerceAtLeast(1)
        return 60_000L / rpm
    }
}
