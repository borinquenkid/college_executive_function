package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class DocxReaderTest : FunSpec({

    test("extractText should read content from calendar.docx") {
        val fixtureFile = File("src/commonTest/resources/calendar.docx")
        if (!fixtureFile.exists()) {
            // Try absolute path if relative fails in different test runners
            val altPath = File("composeApp/src/commonTest/resources/calendar.docx")
            if (!altPath.exists()) throw Exception("Fixture not found at ${fixtureFile.absolutePath}")
        }
        
        val reader = DocxReader()
        val chunks = reader.extractChunks(fixtureFile.absolutePath)
        println("Extracted ${chunks.size} chunks")
        
        val fullText = chunks.joinToString(" ") { it.text }
        fullText shouldContain "MATH 101"
        fullText shouldContain "2026"
        chunks.all { it.type == SourceType.TEXT } shouldBe true
    }
})
