package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking

class LocalFileReaderTest : FunSpec({

    test("readText should read content from a local file") {
        val tempFile = File.createTempFile("test_syllabus", ".txt")
        val expectedContent = "This is a test syllabus content."
        tempFile.writeText(expectedContent)

        val reader = LocalFileReader()
        val actualContent = reader.readText(tempFile.absolutePath)

        actualContent shouldBe expectedContent
        
        tempFile.delete()
    }
})
