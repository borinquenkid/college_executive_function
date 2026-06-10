package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File

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

    test("listFiles should return paths of all files in a directory") {
        val tempDir =
            File(System.getProperty("java.io.tmpdir"), "test_dir_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val file1 = File(tempDir, "file1.txt").apply { writeText("content1") }
        val file2 = File(tempDir, "file2.txt").apply { writeText("content2") }
        val subDir = File(tempDir, "subdir").apply { mkdirs() }
        val fileInSubDir = File(subDir, "file3.txt").apply { writeText("content3") }

        val reader = LocalFileReader()
        val files = reader.listFiles(tempDir.absolutePath)

        files.size shouldBe 2
        files shouldContainExactlyInAnyOrder listOf(file1.absolutePath, file2.absolutePath)

        // Clean up
        file1.delete()
        file2.delete()
        fileInSubDir.delete()
        subDir.delete()
        tempDir.delete()
    }

    test("listFiles should return empty list for non-existent directory") {
        val reader = LocalFileReader()
        val files = reader.listFiles("/non/existent/directory/path/here")
        files shouldBe emptyList()
    }
})
