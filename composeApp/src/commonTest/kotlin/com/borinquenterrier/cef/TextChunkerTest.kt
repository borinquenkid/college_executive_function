package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

class TextChunkerTest : FunSpec({

    test("should chunk text correctly with overlap") {
        val text = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"
        // Chunk size 10, overlap 5
        val chunks = TextChunker.chunk(text, chunkSize = 10, overlap = 5)
        
        chunks.isNotEmpty() shouldBe true
        chunks.forEach { it.type shouldBe SourceType.TEXT }
    }

    test("should handle empty text") {
        TextChunker.chunk("") shouldHaveSize 0
    }

    test("should split at newlines when possible") {
        val text = "First Paragraph\n\nSecond Paragraph"
        val chunks = TextChunker.chunk(text, chunkSize = 20)
        
        chunks.size shouldBe 2
        chunks[0].text shouldBe "First Paragraph"
        chunks[1].text shouldBe "Second Paragraph"
    }
})
