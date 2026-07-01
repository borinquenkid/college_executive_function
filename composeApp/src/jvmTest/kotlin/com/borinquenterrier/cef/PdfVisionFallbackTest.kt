package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

/**
 * Covers the image-only-PDF → Gemini document-vision fallback: detection (PdfTextQuality),
 * the inline-document request body, and SourceNormalizer routing a text-less PDF to vision.
 */
class PdfVisionFallbackTest : FunSpec({

    fun frag(text: String) = SourceFragment(text = text)

    context("PdfTextQuality.isImageOnly") {
        test("empty extraction is image-only") {
            PdfTextQuality.isImageOnly(emptyList()) shouldBe true
        }
        test("a few stray chars (whitespace/page numbers) is image-only") {
            PdfTextQuality.isImageOnly(listOf(frag("  1 \n 2 "))) shouldBe true
        }
        test("a real page of text is not image-only") {
            val page = "ENG 101 601 Summer 2026. Issue Brief #1 due July 1. Independence Day July 3."
            PdfTextQuality.isImageOnly(listOf(frag(page))) shouldBe false
        }
    }

    context("GeminiBodyBuilder.buildDocumentRequestBody") {
        test("carries an inline document part and the instruction") {
            val body = GeminiBodyBuilder.buildDocumentRequestBody(
                prompt = "Transcribe this",
                base64Data = "QUJD",
                mimeType = "application/pdf"
            ).toString()
            body shouldContain "\"inlineData\""
            body shouldContain "\"mimeType\":\"application/pdf\""
            body shouldContain "\"data\":\"QUJD\""
            body shouldContain "Transcribe this"
        }
    }

    context("SourceNormalizer image-only PDF fallback") {

        fun normalizer(
            extracted: List<SourceFragment>,
            ai: AIService?
        ): SourceNormalizer {
            val pdfReader = mockk<PdfReader>(relaxed = true)
            coEvery { pdfReader.readSource(any<ByteArray>()) } returns extracted
            return SourceNormalizer(pdfReader, mockk(relaxed = true), mockk(relaxed = true), ai)
        }

        test("routes a text-less PDF to document vision") {
            val ai = mockk<AIService>(relaxed = true)
            coEvery { ai.extractTextFromDocument(any(), any()) } returns
                "Recovered syllabus text: Issue Brief #1 due July 1, 2026."
            val result = runBlocking {
                normalizer(emptyList(), ai).normalize("%PDF-scan".encodeToByteArray(), SourceFormat.PDF)
            }
            result.joinToString(" ") { it.text } shouldContain "Recovered syllabus text"
            coVerify(exactly = 1) { ai.extractTextFromDocument(any(), any()) }
        }

        test("a text PDF is never sent to vision") {
            val ai = mockk<AIService>(relaxed = true)
            val rich = listOf(frag("A full page of extractable syllabus content ".repeat(5)))
            val result = runBlocking {
                normalizer(rich, ai).normalize("%PDF-text".encodeToByteArray(), SourceFormat.PDF)
            }
            result shouldBe rich
            coVerify(exactly = 0) { ai.extractTextFromDocument(any(), any()) }
        }

        test("no aiService → returns the (empty) extraction, no crash") {
            val result = runBlocking {
                normalizer(emptyList(), null).normalize("%PDF".encodeToByteArray(), SourceFormat.PDF)
            }
            result shouldBe emptyList()
        }
    }
})
