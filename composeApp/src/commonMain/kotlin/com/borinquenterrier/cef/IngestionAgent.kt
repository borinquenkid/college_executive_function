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
    private val driveService: GoogleDriveService,
    private val aiService: AIService,
    private val sourceRepository: SourceRepository? = null
) {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceItem>>(emptyList())
    val sources: StateFlow<List<SourceItem>> = _sources.asStateFlow()

    suspend fun addLocalFile(path: String): SourceItem {
        _isBusy.value = true
        return try {
            AppTracer.current.span("ingestion.add_file") {
                val fileName = path.substringAfterLast("/").substringAfterLast("\\")
                val isIcs = fileName.lowercase().endsWith(".ics")
                setAttribute("source.name", fileName)
                setAttribute("source.type", when {
                    fileName.lowercase().endsWith(".pdf") -> "pdf"
                    fileName.lowercase().endsWith(".docx") -> "docx"
                    isIcs -> "ics"
                    else -> "text"
                })
                val rawFragments = when {
                    fileName.lowercase().endsWith(".docx") -> docxReader.readSource(path)
                    fileName.lowercase().endsWith(".pdf") -> pdfReader.readSource(path)
                    isIcs -> IcsCalendarSource(fileReader.readText(path)).readSource()
                    else -> SourceProcessor.split(fileReader.readText(path))
                }
                val fragments = if (isIcs) rawFragments else WeekAnchorExtractor.inject(rawFragments)
                setAttribute("fragment.count", fragments.size.toLong())
                ContributionValidator.validate(fragments)
                val category = resolveCategory(isIcs, fragments)
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
        return try {
            AppTracer.current.span("ingestion.add_url", mapOf("source.url" to url)) {
                val rawContent = webReader.readTextFromUrl(url)
                val isIcs = url.lowercase().endsWith(".ics")
                setAttribute("source.type", if (isIcs) "ics" else "web")
                val rawFragments = if (isIcs) IcsCalendarSource(rawContent).readSource()
                else SourceProcessor.split(rawContent)
                val fragments = if (isIcs) rawFragments else WeekAnchorExtractor.inject(rawFragments)
                setAttribute("fragment.count", fragments.size.toLong())
                val category = resolveCategory(isIcs, fragments)
                setAttribute("source.category", category.name)
                val sourceItem = SourceItem(url, fragments, category)
                persistSource(sourceItem, url)
                sourceItem
            }
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addDriveFile(file: DriveFile): SourceItem {
        _isBusy.value = true
        return try {
            AppTracer.current.span("ingestion.add_drive_file",
                mapOf("source.name" to file.name, "drive.file_id" to file.id)
            ) {
                val rawContent = driveService.getFileContent(file.id, file.mimeType)
                val isIcs = file.name.lowercase().endsWith(".ics")
                setAttribute("source.type", if (isIcs) "ics" else file.mimeType)
                val rawFragments = if (isIcs) IcsCalendarSource(rawContent).readSource()
                else SourceProcessor.split(rawContent)
                val fragments = if (isIcs) rawFragments else WeekAnchorExtractor.inject(rawFragments)
                setAttribute("fragment.count", fragments.size.toLong())
                val category = resolveCategory(isIcs, fragments)
                setAttribute("source.category", category.name)
                val sourceItem = SourceItem(file.name, fragments, category)
                persistSource(sourceItem, "google_drive://${file.id}")
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
        sourceRepository?.saveSource(item, originUri)
    }
}
