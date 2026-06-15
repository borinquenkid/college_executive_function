package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SourceIngestionHandler(
    private val ingestionAgent: IngestionAgent,
    private val scope: CoroutineScope,
    private val logger: Logger? = null
) {
    fun ingestLocalFile(
        path: String,
        onStart: () -> Unit,
        onSuccess: (SourceItem) -> Unit,
        onFailure: () -> Unit,
        onFinish: () -> Unit
    ) {
        onStart()
        scope.launch {
            try {
                val source = ingestionAgent.addLocalFile(path)
                onSuccess(source)
            } catch (e: Exception) {
                logger?.e("SourceIngestionHandler", "Failed to ingest local file: $path", e)
                onFailure()
            } finally {
                onFinish()
            }
        }
    }

    fun ingestUrl(
        url: String,
        onStart: () -> Unit,
        onSuccess: (SourceItem) -> Unit,
        onFailure: () -> Unit,
        onFinish: () -> Unit
    ) {
        if (url.isBlank()) {
            onFailure()
            return
        }
        onStart()
        scope.launch {
            try {
                val source = ingestionAgent.addUrl(url)
                onSuccess(source)
            } catch (e: Exception) {
                logger?.e("SourceIngestionHandler", "Failed to ingest URL: $url", e)
                onFailure()
            } finally {
                onFinish()
            }
        }
    }

    fun ingestDriveFile(
        file: DriveFile,
        onStart: () -> Unit,
        onSuccess: (SourceItem) -> Unit,
        onFailure: () -> Unit,
        onFinish: () -> Unit
    ) {
        onStart()
        scope.launch {
            try {
                val source = ingestionAgent.addDriveFile(file)
                onSuccess(source)
            } catch (e: Exception) {
                logger?.e("SourceIngestionHandler", "Failed to ingest Drive file: ${file.name}", e)
                onFailure()
            } finally {
                onFinish()
            }
        }
    }
}

object GoogleDriveQueryBuilder {
    fun buildIngestibleFilesQuery(): String {
        return "mimeType = 'application/vnd.google-apps.document' " +
                "or mimeType = 'application/pdf' " +
                "or mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' " +
                "or mimeType = 'text/plain' " +
                "or name contains '.ics'"
    }
}
