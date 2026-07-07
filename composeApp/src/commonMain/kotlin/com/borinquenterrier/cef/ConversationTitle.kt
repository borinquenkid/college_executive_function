package com.borinquenterrier.cef

/**
 * Derives a short conversation title from its first user message (design 2.1, Part A). Kept as a
 * plain object so the truncation logic is unit-testable without a running controller.
 */
object ConversationTitle {
    const val MAX_LENGTH = 40

    /**
     * Collapses whitespace and truncates to [MAX_LENGTH] on a word boundary, appending an ellipsis
     * when the text was cut. Falls back to [Conversation.DEFAULT_TITLE] for blank input.
     */
    fun fromFirstMessage(content: String): String {
        val normalized = content.trim().replace(WHITESPACE, " ")
        if (normalized.isEmpty()) return Conversation.DEFAULT_TITLE
        if (normalized.length <= MAX_LENGTH) return normalized

        val cut = normalized.take(MAX_LENGTH)
        val lastSpace = cut.lastIndexOf(' ')
        val word = if (lastSpace >= MIN_WORD_BOUNDARY) cut.take(lastSpace) else cut
        return word.trimEnd() + "…"
    }

    // A cut shorter than this keeps the hard character cut instead of a too-aggressive word trim.
    private const val MIN_WORD_BOUNDARY = 20
    private val WHITESPACE = Regex("\\s+")
}
