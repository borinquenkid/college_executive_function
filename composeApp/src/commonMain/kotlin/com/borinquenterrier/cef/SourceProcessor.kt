package com.borinquenterrier.cef

/**
 * Utility to process plain text into structured parts for AI processing.
 */
object SourceProcessor {

    /**
     * Processes text into parts. By default, it returns the entire text as a single part.
     */
    fun process(text: String, type: SourceType = SourceType.TEXT): List<SourcePart> {
        if (text.isBlank()) return emptyList()
        return listOf(SourcePart(text = text, type = type))
    }

    /**
     * Splits text into smaller parts if necessary (e.g. for extremely large files).
     */
    fun split(text: String, size: Int = 10000): List<SourcePart> {
        if (text.isBlank()) return emptyList()
        val parts = mutableListOf<SourcePart>()
        var start = 0
        while (start < text.length) {
            val end = (start + size).coerceAtMost(text.length)
            parts.add(SourcePart(text = text.substring(start, end), type = SourceType.TEXT))
            start = end
        }
        return parts
    }
}
