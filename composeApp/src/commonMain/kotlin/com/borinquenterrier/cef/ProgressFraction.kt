package com.borinquenterrier.cef

/**
 * Extracts a determinate progress fraction from a status message that carries an "(i/N)" counter,
 * e.g. "Extracting events from pages 3–4 (2/5)…" → 0.4. Returns null when there is no countable
 * step (so the UI falls back to an indeterminate spinner).
 */
object ProgressFraction {
    private val counter = Regex("""\((\d+)\s*/\s*(\d+)\)""")

    fun parse(text: String): Float? {
        val match = counter.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        if (total <= 0) return null
        return (current.toFloat() / total).coerceIn(0f, 1f)
    }
}
