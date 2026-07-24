package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.io.File
import java.util.UUID

object WebSourceHandler {
    suspend fun handleGetSources(call: ApplicationCall, container: DependencyContainer) {
        val sources = getAllSourceItems(container)
        call.respond(sources)
    }

    suspend fun handlePostSource(call: ApplicationCall, container: DependencyContainer) {
        val payload = try {
            extractMultipartData(call)
        } catch (e: MultipartTooLargeException) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                mapOf("error" to "File exceeds the ${e.maxBytes / (1024 * 1024)}MB upload limit")
            )
            return
        }
        val url = payload.url
        val fileBytes = payload.fileBytes
        val fileName = payload.fileName

        when {
            url != null -> handleMultipartUrl(call, url, container)
            fileBytes != null && fileName != null -> handleMultipartFile(call, fileName, fileBytes, container)
            else -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing url or file parameter"))
        }
    }

    private suspend fun handleMultipartUrl(call: ApplicationCall, url: String, container: DependencyContainer) {
        val sourceItem = processUrlIngestion(url, container)
        call.respond(HttpStatusCode.Accepted, sourceItem)
    }

    private suspend fun handleMultipartFile(call: ApplicationCall, fileName: String, fileBytes: ByteArray, container: DependencyContainer) {
        val sourceItem = processFileIngestion(fileName, fileBytes, container)
        call.respond(HttpStatusCode.Accepted, sourceItem)
    }

    // Parsing + categorization (ingestionAgent.addUrl) stays synchronous — it's a quick single AI
    // call, not the slow multi-step chain. Only the pipeline (context analysis → extraction →
    // conflict resolution → calendar write, the actual 20-30s cost — see ADR 0012) moves to the
    // background, so the response returns once the source is durably persisted with PENDING status
    // rather than once digestion finishes.
    private suspend fun processUrlIngestion(url: String, container: DependencyContainer): SourceItem {
        val sourceItem = container.ingestionAgent.addUrl(url)
        container.launchInBackground { container.sourceProcessingPipeline.processSource(sourceItem) }
        return sourceItem
    }

    private suspend fun processFileIngestion(fileName: String, bytes: ByteArray, container: DependencyContainer): SourceItem {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cef-" + UUID.randomUUID().toString())
        tempDir.mkdirs()
        val cleanTempFile = File(tempDir, fileName)
        return try {
            cleanTempFile.writeBytes(bytes)
            // Durable copy lands in the source's DB row (see SqlDelightSourceRepository) before this
            // temp file is deleted below — the temp file is a scratch buffer, not the only copy.
            val sourceItem = container.ingestionAgent.addLocalFile(cleanTempFile.absolutePath, bytes)
            container.launchInBackground { container.sourceProcessingPipeline.processSource(sourceItem) }
            sourceItem
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
