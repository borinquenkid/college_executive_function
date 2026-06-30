package com.borinquenterrier.cef

/**
 * Locates the slice of source text most relevant to a deliverable title, so the date-picker
 * dialog can show the user the evidence next to the picker. Scores each sentence by how many
 * substantive words it shares with the title; returns the best match, or null when nothing
 * overlaps — a signal the deliverable is likely fabricated rather than merely mis-dated.
 */
object SourceSnippetExtractor {

    private val WORD = Regex("""[a-z0-9]+""")
    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")
    private const val MIN_WORD_LEN = 3

    private fun significantWords(text: String): List<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filter { it.length >= MIN_WORD_LEN }.toList()

    fun snippet(sourceText: String, title: String): String? {
        val titleWords = significantWords(title).toSet()
        if (titleWords.isEmpty()) return null

        val candidates = sourceText
            .split('\n')
            .flatMap { it.split(SENTENCE_SPLIT) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var best: String? = null
        var bestScore = 0
        for (candidate in candidates) {
            val words = significantWords(candidate).toSet()
            val score = titleWords.count { it in words }
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return if (bestScore > 0) best else null
    }
}
