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
            val fileName = path.substringAfterLast("/").substringAfterLast("\\")
            val fragments = when {
                fileName.lowercase().endsWith(".docx") -> docxReader.readSource(path)
                fileName.lowercase().endsWith(".pdf") -> pdfReader.readSource(path)
                fileName.lowercase()
                    .endsWith(".ics") -> IcsCalendarSource(fileReader.readText(path)).readSource()

                else -> SourceProcessor.split(fileReader.readText(path))
            }
            ContributionValidator.validate(fragments)
            val isIcs = fileName.lowercase().endsWith(".ics")
            val sourceItem = SourceItem(fileName, fragments, resolveCategory(isIcs, fragments))
            persistSource(sourceItem, path)
            sourceItem
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addUrl(url: String): SourceItem {
        _isBusy.value = true
        return try {
            val rawContent = webReader.readTextFromUrl(url)
            val isIcs = url.lowercase().endsWith(".ics")
            val fragments = if (isIcs) IcsCalendarSource(rawContent).readSource()
            else SourceProcessor.split(rawContent)
            val sourceItem = SourceItem(url, fragments, resolveCategory(isIcs, fragments))
            persistSource(sourceItem, url)
            sourceItem
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addDriveFile(file: DriveFile): SourceItem {
        _isBusy.value = true
        return try {
            val rawContent = driveService.getFileContent(file.id, file.mimeType)
            val isIcs = file.name.lowercase().endsWith(".ics")
            val fragments = if (isIcs) IcsCalendarSource(rawContent).readSource()
            else SourceProcessor.split(rawContent)
            val sourceItem = SourceItem(file.name, fragments, resolveCategory(isIcs, fragments))
            persistSource(sourceItem, "google_drive://${file.id}")
            sourceItem
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
