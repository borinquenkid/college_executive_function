package com.borinquenterrier.cef

/**
 * Splits a pasted blob of links (newline-, comma-, or whitespace-separated) into individual,
 * de-duplicated URLs so the URL source box can accept multiple links at once.
 */
object UrlListParser {
    private val SEPARATORS = Regex("""[\s,]+""")

    fun parse(text: String): List<String> =
        text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
