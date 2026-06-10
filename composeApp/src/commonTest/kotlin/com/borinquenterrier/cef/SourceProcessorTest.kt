package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SourceProcessorTest : FunSpec({

    test("should process text into a single part by default") {
        val text = "This is a test document content."
        val parts = SourceProcessor.process(text)

        parts shouldHaveSize 1
        parts[0].text shouldBe text
        parts[0].type shouldBe SourceType.TEXT
    }

    test("should handle empty text") {
        SourceProcessor.process("") shouldHaveSize 0
    }

    test("should split large text correctly") {
        val text = "01234567890123456789" // 20 chars
        val parts = SourceProcessor.split(text, size = 10)

        parts.size shouldBe 2
        parts[0].text shouldBe "0123456789"
        parts[1].text shouldBe "0123456789"
    }
})
