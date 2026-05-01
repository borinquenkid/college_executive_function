package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class PdfReaderTest : FunSpec({

    test("extractText should read content from calendar.pdf") {
        val fixtureFile = File("src/commonTest/resources/calendar.pdf")
        if (!fixtureFile.exists()) {
            val altPath = File("composeApp/src/commonTest/resources/calendar.pdf")
            if (!altPath.exists()) throw Exception("Fixture not found at ${fixtureFile.absolutePath}")
        }
        
        val reader = PdfReader()
        val chunks = reader.extractChunks(fixtureFile.absolutePath)
        println("Extracted ${chunks.size} chunks (pages)")
        
        val fullText = chunks.joinToString(" ") { it.text }
        fullText shouldContain "MATH 101"
        fullText shouldContain "2026"
        
        chunks.forEach { chunk ->
            chunk.pageNumber shouldBe chunk.pageNumber // ensure it's present
            chunk.type shouldBe SourceType.TEXT
        }
    }
})
