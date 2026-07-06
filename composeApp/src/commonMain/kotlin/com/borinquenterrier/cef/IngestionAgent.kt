package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles the logic for adding and processing different types of sources.
 * Decoupled from the UI to allow for headless testing.
 */
class IngestionAgent(
    private val fileReader: LocalFileReader,
    private val docxReader: DocxReader,
    private val pdfReader: PdfReader,
    private val webReader: WebSourceReader,
    private val aiService: AIService,
    private val sourceRepository: SourceRepository
) {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceItem>>(emptyList())
    val sources: StateFlow<List<SourceItem>> = _sources.asStateFlow()

    private val _largeDocumentNotice = MutableStateFlow<String?>(null)

    /** Non-null when the document currently being ingested is large enough to route through the
     * slower, quota-heavier Gemini Files API. Dismissible — purely informational, never blocks
     * ingestion. Cleared at the start of the next ingest call. */
    val largeDocumentNotice: StateFlow<String?> = _largeDocumentNotice.asStateFlow()

    // Single format→fragments dispatch shared by the ingestion paths. aiService enables the
    // image-only-PDF vision fallback.
    private val normalizer = SourceNormalizer(
        pdfReader,
        docxReader,
        webReader,
        aiService,
        onLargeDocumentDetected = { _largeDocumentNotice.value = LARGE_DOCUMENT_NOTICE }
    )

    suspend fun addLocalFile(path: String): SourceItem {
        _isBusy.value = true
        _largeDocumentNotice.value = null
        return try {
            AppTracer.current.span("ingestion.add_file") {
                val fileName = fileReader.resolveDisplayName(path).ifBlank {
                    path.substringAfterLast("/").substringAfterLast("\\")
                }
                val format = SourceFormatDetector.detect(fileName)
                setAttribute("source.name_hash", TelemetryIdHasher.hash(fileName))
                setAttribute("source.type", format.name)
                val rawFragments = normalizer.normalize(fileReader.readBytes(path), format)
                val fragments = if (format == SourceFormat.ICS) rawFragments else WeekAnchorExtractor.inject(rawFragments)
                setAttribute("fragment.count", fragments.size.toLong())
                ContributionValidator.validate(fragments)
                val category = resolveCategory(format == SourceFormat.ICS, fragments)
                setAttribute("source.category", category.name)
                val sourceItem = SourceItem(fileName, fragments, category)
                persistSource(sourceItem, path)
                sourceItem
            }
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addUrl(url: String): SourceItem {
        _isBusy.value = true
        _largeDocumentNotice.value = null
        return try {
            AppTracer.current.span("ingestion.add_url", mapOf("source.url_hash" to TelemetryIdHasher.hash(url))) {
                // Web pages default to HTML; a URL ending .pdf/.ics routes to that extractor.
                val format = SourceFormatDetector.detect(url, default = SourceFormat.HTML)
                setAttribute("source.type", format.name)
                val rawFragments = normalizer.normalize(webReader.readBytesFromUrl(url), format)
                val fragments = if (format == SourceFormat.ICS) rawFragments else WeekAnchorExtractor.inject(rawFragments)
                setAttribute("fragment.count", fragments.size.toLong())
                val category = resolveCategory(format == SourceFormat.ICS, fragments)
                setAttribute("source.category", category.name)
                val sourceItem = SourceItem(url, fragments, category)
                persistSource(sourceItem, url)
                sourceItem
            }
        } finally {
            _isBusy.value = false
        }
    }

    /**
     * Determines the [SourceCategory] for a newly ingested source.
     * ICS sources are always [SourceCategory.CALENDAR] (and must be non-empty).
     * All other sources are categorized by the AI service.
     */
    private suspend fun resolveCategory(
        isIcs: Boolean,
        fragments: List<SourceFragment>
    ): SourceCategory {
        return if (isIcs) {
            if (fragments.isEmpty()) throw SourceValidationException(
                "Calendar must contain at least one day-long event, deadline, or holiday."
            )
            SourceCategory.CALENDAR
        } else {
            val fullText = fragments.joinToString("\n\n") { it.text }
            aiService.categorizeSource(fullText)
        }
    }

    private suspend fun persistSource(item: SourceItem, originUri: String?) {
        sourceRepository.saveSource(item, originUri)
    }

    companion object {
        internal const val LARGE_DOCUMENT_NOTICE =
            "This document is large — processing may take longer and use more of your Gemini API quota."
    }
}
