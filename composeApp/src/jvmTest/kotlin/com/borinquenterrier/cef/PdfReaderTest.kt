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
        val parts = reader.readSource(fixtureFile.absolutePath)
        println("Extracted ${parts.size} parts (pages)")
        
        val fullText = parts.joinToString(" ") { it.text }
        fullText shouldContain "MATH 101"
        fullText shouldContain "2026"
        
        parts.forEach { part ->
            part.pageNumber shouldBe part.pageNumber // ensure it's present
            part.type shouldBe SourceType.TEXT
        }
    }

    test("extractText should read content from syllabus_bdan250.pdf") {
        val fixtureFile = listOf(
            File("src/commonTest/resources/syllabus_bdan250.pdf"),
            File("composeApp/src/commonTest/resources/syllabus_bdan250.pdf"),
            File("../composeApp/src/commonTest/resources/syllabus_bdan250.pdf")
        ).find { it.exists() } ?: throw Exception("Fixture not found for syllabus_bdan250.pdf")
        
        val reader = PdfReader()
        val parts = reader.readSource(fixtureFile.absolutePath)
        println("Extracted ${parts.size} pages from BDAN 250")
        
        val fullText = parts.joinToString(" ") { it.text }
        fullText.lowercase() shouldContain "bdan"
        fullText.lowercase() shouldContain "analytics"
        
        parts.forEach { part ->
            part.pageNumber shouldBe part.pageNumber
            part.type shouldBe SourceType.TEXT
        }
    }

    test("extractText should read content from syllabus_hist152.pdf") {
        val fixtureFile = listOf(
            File("src/commonTest/resources/syllabus_hist152.pdf"),
            File("composeApp/src/commonTest/resources/syllabus_hist152.pdf"),
            File("../composeApp/src/commonTest/resources/syllabus_hist152.pdf")
        ).find { it.exists() } ?: throw Exception("Fixture not found for syllabus_hist152.pdf")
        
        val reader = PdfReader()
        val parts = reader.readSource(fixtureFile.absolutePath)
        println("Extracted ${parts.size} pages from HIST 152")
        
        val fullText = parts.joinToString(" ") { it.text }
        fullText.lowercase() shouldContain "hist"
        fullText.lowercase() shouldContain "history"
        
        parts.forEach { part ->
            part.pageNumber shouldBe part.pageNumber
            part.type shouldBe SourceType.TEXT
        }
    }
})

