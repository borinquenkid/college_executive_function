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
        val text = reader.extractText(fixtureFile.absolutePath)
        
        text shouldContain "MATH 101"
        text shouldContain "2026"
    }
})
