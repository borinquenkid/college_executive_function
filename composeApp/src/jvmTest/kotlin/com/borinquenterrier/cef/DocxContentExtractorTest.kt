package com.borinquenterrier.cef

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates the exact ZipReader/Inflate/regex pipeline DocxReader.ios.kt runs on a real,
 * production .docx fixture — not a synthetic archive built by this test suite. Runs on JVM so
 * it's fast to iterate on without needing a device or simulator; the logic under test is pure
 * common Kotlin, so this is testing precisely what iOS runs, not a JVM-only stand-in for it.
 */
class DocxContentExtractorTest {

    private fun findFixture(): File =
        listOf(
            File("src/commonTest/resources/calendar.docx"),
            File("composeApp/src/commonTest/resources/calendar.docx"),
        ).firstOrNull { it.exists() }
            ?: throw Exception("Fixture calendar.docx not found")

    @Test
    fun `extracts real syllabus content from the calendar docx fixture`() {
        val bytes = findFixture().readBytes()
        val text = DocxContentExtractor.extractPlainText(bytes)

        assertNotNull(text, "expected word/document.xml to be found and decompressed")
        assertTrue(text.contains("MATH 101"), "expected extracted text to contain 'MATH 101', got: $text")
        assertTrue(text.contains("2026"), "expected extracted text to contain '2026', got: $text")
        assertTrue(text.none { it.code in 1..8 }, "extracted text should not contain raw control bytes from a bad inflate")
    }

    @Test
    fun `throws a clear error for a non-ZIP file rather than a silent wrong result`() {
        val notADocx = "this is plain text, not a zip".encodeToByteArray()
        assertFailsWith<ZipReader.ZipException> {
            DocxContentExtractor.extractPlainText(notADocx)
        }
    }
}
