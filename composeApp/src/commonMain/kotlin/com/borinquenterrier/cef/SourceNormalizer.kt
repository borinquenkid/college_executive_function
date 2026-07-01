package com.borinquenterrier.cef

/** The document formats the app can ingest. Everything normalizes into text [SourceFragment]s. */
enum class SourceFormat { PDF, DOCX, ICS, HTML, TEXT }

object SourceFormatDetector {
    /** Detect a [SourceFormat] from a filename or MIME type; [default] applies when nothing matches. */
    fun detect(nameOrMime: String, default: SourceFormat = SourceFormat.TEXT): SourceFormat {
        val s = nameOrMime.lowercase()
        return when {
            s.endsWith(".pdf") || "pdf" in s -> SourceFormat.PDF
            s.endsWith(".docx") || "wordprocessingml" in s -> SourceFormat.DOCX
            s.endsWith(".ics") || "calendar" in s -> SourceFormat.ICS
            s.endsWith(".html") || s.endsWith(".htm") || "html" in s -> SourceFormat.HTML
            else -> default
        }
    }
}

/**
 * The single format→fragments dispatch. Every ingestion path funnels its raw bytes through here,
 * so no path can skip PDF/DOCX text extraction (the Drive bug: raw %PDF bytes were `split()` as
 * text). PDF/DOCX go to their extractors; ICS/HTML/plain-text decode and process directly.
 */
class SourceNormalizer(
    private val pdfReader: PdfReader,
    private val docxReader: DocxReader,
    private val webReader: WebSourceReader,
    // Optional: when provided, an image-only PDF (no extractable text) is OCR'd via the model's
    // document vision instead of yielding empty fragments. Null keeps the pure text-only behavior.
    private val aiService: AIService? = null,
) {
    suspend fun normalize(bytes: ByteArray, format: SourceFormat): List<SourceFragment> = when (format) {
        SourceFormat.PDF -> normalizePdf(bytes)
        SourceFormat.DOCX -> docxReader.readSource(bytes)
        SourceFormat.ICS -> IcsCalendarSource(bytes.decodeToString()).readSource()
        SourceFormat.HTML -> SourceProcessor.split(webReader.cleanHtml(bytes.decodeToString()))
        SourceFormat.TEXT -> SourceProcessor.split(bytes.decodeToString())
    }

    private suspend fun normalizePdf(bytes: ByteArray): List<SourceFragment> {
        val extracted = pdfReader.readSource(bytes)
        if (aiService == null || !PdfTextQuality.isImageOnly(extracted)) return extracted
        // Scanned/image-only PDF: recover the text with document vision.
        val visionText = aiService.extractTextFromDocument(bytes, "application/pdf")
        return if (!visionText.isNullOrBlank()) SourceProcessor.split(visionText) else extracted
    }
}
