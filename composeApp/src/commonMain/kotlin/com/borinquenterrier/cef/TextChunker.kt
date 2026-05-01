package com.borinquenterrier.cef

/**
 * Utility to split plain text into semantic chunks for AI processing.
 */
object TextChunker {

    /**
     * Splits text into chunks of roughly [chunkSize] characters, 
     * attempting to split at newlines to preserve sentence/paragraph integrity.
     */
    fun chunk(text: String, chunkSize: Int = 3000, overlap: Int = 200): List<SourceChunk> {
        if (text.isBlank()) return emptyList()
        
        val chunks = mutableListOf<SourceChunk>()
        var start = 0
        
        while (start < text.length) {
            var end = start + chunkSize
            
            if (end < text.length) {
                // Try to find a newline near the end to split cleanly
                val lastNewline = text.lastIndexOf('\n', end)
                if (lastNewline > start + (chunkSize / 2)) {
                    end = lastNewline
                }
            } else {
                end = text.length
            }
            
            val chunkText = text.substring(start, end).trim()
            if (chunkText.isNotEmpty()) {
                chunks.add(SourceChunk(text = chunkText, type = SourceType.TEXT))
            }
            
            start = end - overlap
            if (start < 0) start = 0
            if (start >= text.length || end == text.length) break
            
            // Ensure progress
            if (start <= chunks.last().text.length - chunkText.length + start) {
                start = end
            }
        }
        
        return chunks
    }
}
