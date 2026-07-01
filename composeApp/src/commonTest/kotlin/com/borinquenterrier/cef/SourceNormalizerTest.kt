package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * The single format→fragments dispatch. The bug this replaces: the Drive path sent raw PDF
 * bytes to `split()` as if they were text. The normalizer routes each format to the right
 * extractor so no path can skip PDF/DOCX text extraction again.
 */
class SourceNormalizerTest : FunSpec({

    fun normalizer(pdf: PdfReader = mockk(), docx: DocxReader = mockk(), web: WebSourceReader = mockk()) =
        SourceNormalizer(pdf, docx, web)

    // ── format detection ──────────────────────────────────────────────────────

    test("detects format from filename extension") {
        SourceFormatDetector.detect("syllabus.pdf") shouldBe SourceFormat.PDF
        SourceFormatDetector.detect("notes.docx") shouldBe SourceFormat.DOCX
        SourceFormatDetector.detect("cal.ics") shouldBe SourceFormat.ICS
        SourceFormatDetector.detect("page.html") shouldBe SourceFormat.HTML
        SourceFormatDetector.detect("readme.txt") shouldBe SourceFormat.TEXT
    }

    test("detects format from MIME type") {
        SourceFormatDetector.detect("application/pdf") shouldBe SourceFormat.PDF
        SourceFormatDetector.detect("application/vnd.openxmlformats-officedocument.wordprocessingml.document") shouldBe SourceFormat.DOCX
        SourceFormatDetector.detect("text/calendar") shouldBe SourceFormat.ICS
    }

    test("falls back to the supplied default when nothing matches (web → HTML)") {
        SourceFormatDetector.detect("https://example.com/page", default = SourceFormat.HTML) shouldBe SourceFormat.HTML
        SourceFormatDetector.detect("mystery-blob") shouldBe SourceFormat.TEXT
    }

    // ── dispatch ──────────────────────────────────────────────────────────────

    test("PDF routes to the PDF reader's bytes overload (never split as text)") {
        val pdf = mockk<PdfReader>()
        val expected = listOf(SourceFragment("page 1 text", pageNumber = 1))
        coEvery { pdf.readSource(any<ByteArray>()) } returns expected

        val result = normalizer(pdf = pdf).normalize("%PDF-1.4 …".encodeToByteArray(), SourceFormat.PDF)

        result shouldBe expected
        coVerify(exactly = 1) { pdf.readSource(any<ByteArray>()) }
    }

    test("DOCX routes to the DOCX reader's bytes overload") {
        val docx = mockk<DocxReader>()
        coEvery { docx.readSource(any<ByteArray>()) } returns listOf(SourceFragment("doc text"))
        normalizer(docx = docx).normalize(ByteArray(4), SourceFormat.DOCX)
        coVerify(exactly = 1) { docx.readSource(any<ByteArray>()) }
    }

    test("TEXT is chunked as plain text") {
        val result = normalizer().normalize("Essay due July 15.".encodeToByteArray(), SourceFormat.TEXT)
        result.first().text shouldBe "Essay due July 15."
    }

    test("HTML is cleaned then chunked") {
        val web = mockk<WebSourceReader>()
        every { web.cleanHtml(any()) } returns "Cleaned page text"
        val result = normalizer(web = web).normalize("<p>Cleaned page text</p>".encodeToByteArray(), SourceFormat.HTML)
        result.first().text shouldBe "Cleaned page text"
        verify { web.cleanHtml(any()) }
    }
})
