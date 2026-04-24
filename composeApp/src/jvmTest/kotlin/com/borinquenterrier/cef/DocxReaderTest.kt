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
        val text = reader.extractText(fixtureFile.absolutePath)
        println("Extracted text: $text")
        
        text shouldContain "MATH 101"
        text shouldContain "2026"
        // Add more specific expectations based on what's in your docx
    }
})
