package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles the logic for adding and processing different types of sources.
 * Decoupled from the UI to allow for headless testing.
 */
class SourceFlow(
    private val fileReader: LocalFileReader,
    private val docxReader: DocxReader,
    private val pdfReader: PdfReader,
    private val webReader: WebSourceReader,
    private val driveService: GoogleDriveService,
    private val aiService: AIService
) {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun addLocalFile(path: String): SourceItem {
        _isBusy.value = true
        return try {
            val fileName = path.substringAfterLast("/").substringAfterLast("\\")
            val parts = when {
                fileName.lowercase().endsWith(".docx") -> docxReader.readSource(path)
                fileName.lowercase().endsWith(".pdf") -> pdfReader.readSource(path)
                fileName.lowercase().endsWith(".ics") -> {
                    val raw = fileReader.readText(path)
                    IcsCalendarSource(raw).readSource()
                }
                else -> SourceProcessor.process(fileReader.readText(path))
            }
            SourceItem(fileName, parts)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addUrl(url: String): SourceItem {
        _isBusy.value = true
        return try {
            val rawContent = webReader.readTextFromUrl(url)
            val parts = if (url.lowercase().endsWith(".ics")) {
                IcsCalendarSource(rawContent).readSource()
            } else {
                SourceProcessor.process(rawContent)
            }
            SourceItem(url, parts)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addDriveFile(file: DriveFile): SourceItem {
        _isBusy.value = true
        return try {
            val rawContent = driveService.getFileContent(file.id, file.mimeType)
            val parts = when {
                file.name.lowercase().endsWith(".ics") -> IcsCalendarSource(rawContent).readSource()
                else -> SourceProcessor.process(rawContent)
            }
            SourceItem(file.name, parts)
        } finally {
            _isBusy.value = false
        }
    }
}
