package com.borinquenterrier.cef

/**
 * Extracts a determinate progress fraction from a status message that carries an "(i/N)" counter,
 * e.g. "Extracting events from pages 3–4 (2/5)…" → 0.4. Returns null when there is no *multi-step*
 * counter, so the UI falls back to an indeterminate spinner rather than showing a misleading full
 * bar: a single-batch "(1/1)" isn't real progress (extraction/push are still running after it).
 */
object ProgressFraction {
    private val counter = Regex("""\((\d+)\s*/\s*(\d+)\)""")

    fun parse(text: String): Float? {
        val match = counter.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        if (total <= 1) return null // single step → not meaningful progress; use the spinner
        return (current.toFloat() / total).coerceIn(0f, 1f)
    }
}
