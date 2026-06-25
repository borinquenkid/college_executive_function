package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private val DOC = DriveFile("1", "Alpha.gdoc", "application/vnd.google-apps.document")
private val PDF = DriveFile("2", "Bravo.pdf", "application/pdf")
private val DOCX = DriveFile("3", "Charlie.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
private val ICS = DriveFile("4", "Delta.ics", "text/calendar")
private val ICS_NO_MIME = DriveFile("5", "Echo.ics", "application/octet-stream")
private val ALL = listOf(DOC, PDF, DOCX, ICS, ICS_NO_MIME)

class DriveFileFilterTest : StringSpec({
    val filter = DriveFileFilter()

    // --- filter: query ---

    "empty query with no type returns all files" {
        filter.filter(ALL, "", null) shouldBe ALL
    }

    "blank query with no type returns all files" {
        filter.filter(ALL, "   ", null) shouldBe ALL
    }

    "name query matches substring case-insensitively" {
        val result = filter.filter(ALL, "alpha", null)
        result shouldContainExactly listOf(DOC)
    }

    "name query matching nothing returns empty list" {
        filter.filter(ALL, "zzz", null).shouldBeEmpty()
    }

    // --- filter: type ---

    "type PDF returns only pdf files" {
        val result = filter.filter(ALL, "", DriveFileType.PDF)
        result shouldContainExactly listOf(PDF)
    }

    "type GOOGLE_DOC returns only google docs" {
        val result = filter.filter(ALL, "", DriveFileType.GOOGLE_DOC)
        result shouldContainExactly listOf(DOC)
    }

    "type DOCX returns only docx files" {
        val result = filter.filter(ALL, "", DriveFileType.DOCX)
        result shouldContainExactly listOf(DOCX)
    }

    "type ICS matches by filename extension regardless of MIME type" {
        val result = filter.filter(ALL, "", DriveFileType.ICS)
        result shouldContainExactly listOf(ICS, ICS_NO_MIME)
    }

    "null type returns all files" {
        filter.filter(ALL, "", null) shouldBe ALL
    }

    // --- filter: combined ---

    "query and type filter is an intersection" {
        val files = listOf(
            DriveFile("a", "Spring Syllabus.pdf", "application/pdf"),
            DriveFile("b", "Fall Syllabus.pdf", "application/pdf"),
            DriveFile("c", "Spring Notes.gdoc", "application/vnd.google-apps.document")
        )
        val result = filter.filter(files, "spring", DriveFileType.PDF)
        result.map { it.id } shouldContainExactly listOf("a")
    }

    // --- sort ---

    "sort returns files in alphabetical order" {
        val files = listOf(
            DriveFile("1", "Zebra.pdf", "application/pdf"),
            DriveFile("2", "apple.pdf", "application/pdf"),
            DriveFile("3", "Mango.pdf", "application/pdf")
        )
        val sorted = filter.sort(files)
        sorted.map { it.name } shouldContainExactly listOf("apple.pdf", "Mango.pdf", "Zebra.pdf")
    }

    "sort is case-insensitive" {
        val files = listOf(
            DriveFile("1", "b.pdf", "application/pdf"),
            DriveFile("2", "A.pdf", "application/pdf")
        )
        filter.sort(files).map { it.name } shouldContainExactly listOf("A.pdf", "b.pdf")
    }

    // --- DriveFileType.from ---

    "DriveFileType.from resolves Google Doc" {
        DriveFileType.from(DOC) shouldBe DriveFileType.GOOGLE_DOC
    }

    "DriveFileType.from resolves PDF" {
        DriveFileType.from(PDF) shouldBe DriveFileType.PDF
    }

    "DriveFileType.from resolves DOCX" {
        DriveFileType.from(DOCX) shouldBe DriveFileType.DOCX
    }

    "DriveFileType.from resolves ICS by filename" {
        DriveFileType.from(ICS_NO_MIME) shouldBe DriveFileType.ICS
    }

    "DriveFileType.from returns null for unknown type" {
        val unknown = DriveFile("x", "notes.txt", "text/plain")
        DriveFileType.from(unknown) shouldBe null
    }
})
