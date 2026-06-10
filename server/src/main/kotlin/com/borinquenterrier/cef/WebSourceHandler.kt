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
        val payload = extractMultipartData(call)
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
        call.respond(HttpStatusCode.OK, sourceItem)
    }

    private suspend fun handleMultipartFile(call: ApplicationCall, fileName: String, fileBytes: ByteArray, container: DependencyContainer) {
        val sourceItem = processFileIngestion(fileName, fileBytes, container)
        call.respond(HttpStatusCode.OK, sourceItem)
    }

    private suspend fun processUrlIngestion(url: String, container: DependencyContainer): SourceItem {
        val sourceItem = container.ingestionAgent.addUrl(url)
        container.sourceProcessingPipeline.processSource(sourceItem)
        return sourceItem
    }

    private suspend fun processFileIngestion(fileName: String, bytes: ByteArray, container: DependencyContainer): SourceItem {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cef-" + UUID.randomUUID().toString())
        tempDir.mkdirs()
        val cleanTempFile = File(tempDir, fileName)
        return try {
            cleanTempFile.writeBytes(bytes)
            val sourceItem = container.ingestionAgent.addLocalFile(cleanTempFile.absolutePath)
            container.sourceProcessingPipeline.processSource(sourceItem)
            sourceItem
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
