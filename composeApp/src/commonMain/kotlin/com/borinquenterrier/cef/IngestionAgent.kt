package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

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
                fileName.lowercase().endsWith(".ics") -> {
                    val raw = fileReader.readText(path)
                    IcsCalendarSource(raw).readSource()
                }
                else -> SourceProcessor.process(fileReader.readText(path))
            }
            val category = if (fileName.lowercase().endsWith(".ics")) {
                if (fragments.isEmpty()) {
                    throw SourceValidationException("Calendar must contain at least one day-long event, deadline, or holiday.")
                }
                SourceCategory.CALENDAR
            } else {
                val fullText = fragments.joinToString("\n\n") { it.text }
                aiService.categorizeSource(fullText)
            }
            val sourceItem = SourceItem(fileName, fragments, category)
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
            val fragments = if (url.lowercase().endsWith(".ics")) {
                IcsCalendarSource(rawContent).readSource()
            } else {
                SourceProcessor.process(rawContent)
            }
            val category = if (url.lowercase().endsWith(".ics")) {
                if (fragments.isEmpty()) {
                    throw SourceValidationException("Calendar must contain at least one day-long event, deadline, or holiday.")
                }
                SourceCategory.CALENDAR
            } else {
                val fullText = fragments.joinToString("\n\n") { it.text }
                aiService.categorizeSource(fullText)
            }
            val sourceItem = SourceItem(url, fragments, category)
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
            val fragments = when {
                file.name.lowercase().endsWith(".ics") -> IcsCalendarSource(rawContent).readSource()
                else -> SourceProcessor.process(rawContent)
            }
            val category = if (file.name.lowercase().endsWith(".ics")) {
                if (fragments.isEmpty()) {
                    throw SourceValidationException("Calendar must contain at least one day-long event, deadline, or holiday.")
                }
                SourceCategory.CALENDAR
            } else {
                val fullText = fragments.joinToString("\n\n") { it.text }
                aiService.categorizeSource(fullText)
            }
            val sourceItem = SourceItem(file.name, fragments, category)
            persistSource(sourceItem, "google_drive://${file.id}")
            sourceItem
        } finally {
            _isBusy.value = false
        }
    }

    private suspend fun persistSource(item: SourceItem, originUri: String?) {
        sourceRepository?.saveSource(item, originUri)
    }
}
