package com.borinquenterrier.cef

/**
 * Utility to process plain text into structured fragments for AI processing.
 */
object SourceProcessor {

    /**
     * Processes text into fragments. By default, it returns the entire text as a single fragment.
     */
    fun process(text: String, type: SourceType = SourceType.TEXT): List<SourceFragment> {
        if (text.isBlank()) return emptyList()
        return listOf(SourceFragment(text = text, type = type))
    }

    /**
     * Splits text into smaller fragments if necessary (e.g. for extremely large files).
     */
    fun split(text: String, size: Int = 3000): List<SourceFragment> {
        if (text.isBlank()) return emptyList()
        val fragments = mutableListOf<SourceFragment>()
        var start = 0
        var pageNum = 1
        while (start < text.length) {
            val end = (start + size).coerceAtMost(text.length)
            fragments.add(
                SourceFragment(
                    text = text.substring(start, end),
                    pageNumber = pageNum++,
                    type = SourceType.TEXT
                )
            )
            start = end
        }
        return fragments
    }
}
