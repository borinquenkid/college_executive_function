package com.borinquenterrier.cef

/**
 * Locates the slice of source text most relevant to a deliverable title, so the date-picker
 * dialog can show the user the evidence next to the picker. Ranks each sentence against the
 * title with [Bm25Ranker]; returns the best match, or null when nothing overlaps — a signal
 * the deliverable is likely fabricated rather than merely mis-dated.
 */
object SourceSnippetExtractor {

    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

    fun snippet(sourceText: String, title: String): String? {
        val candidates = sourceText
            .split('\n')
            .flatMap { it.split(SENTENCE_SPLIT) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return null

        val best = Bm25Ranker.rank(title, candidates).firstOrNull() ?: return null
        return if (best.score > 0.0) candidates[best.index] else null
    }
}
