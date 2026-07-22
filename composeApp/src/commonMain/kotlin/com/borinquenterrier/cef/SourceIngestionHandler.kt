package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SourceIngestionHandler(
    private val ingestionAgent: IngestionAgent,
    private val scope: CoroutineScope
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
                println("[SourceIngestionHandler] Failed to ingest local file '$path': $e")
                onFailure()
            } finally {
                onFinish()
            }
        }
    }

    fun ingestLocalFiles(
        paths: List<String>,
        onStart: () -> Unit,
        onEachSuccess: (SourceItem) -> Unit,
        onFinish: () -> Unit
    ) {
        onStart()
        scope.launch {
            for (path in paths) {
                try {
                    val source = ingestionAgent.addLocalFile(path)
                    onEachSuccess(source)
                } catch (e: Exception) {
                    println("[SourceIngestionHandler] Failed to ingest local file '$path', skipping: $e")
                }
            }
            onFinish()
        }
    }

    fun ingestUrls(
        urls: List<String>,
        onStart: () -> Unit,
        onEachSuccess: (SourceItem) -> Unit,
        onFinish: () -> Unit
    ) {
        onStart()
        scope.launch {
            for (url in urls) {
                try {
                    val source = ingestionAgent.addUrl(url)
                    onEachSuccess(source)
                } catch (e: Exception) {
                    println("[SourceIngestionHandler] Failed to ingest url '$url', skipping: $e")
                }
            }
            onFinish()
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
                println("[SourceIngestionHandler] Failed to ingest url '$url': $e")
                onFailure()
            } finally {
                onFinish()
            }
        }
    }

}
