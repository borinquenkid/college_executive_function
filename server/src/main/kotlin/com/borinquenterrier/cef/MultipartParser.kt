package com.borinquenterrier.cef

import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart

internal class MultipartPayload(
    val url: String? = null,
    val fileBytes: ByteArray? = null,
    val fileName: String? = null
)

private class MultipartBuilder {
    var url: String? = null
    var fileBytes: ByteArray? = null
    var fileName: String? = null
}

private suspend fun processPart(part: PartData, builder: MultipartBuilder) {
    when (part) {
        is PartData.FormItem -> {
            if (part.name == "url") {
                builder.url = part.value
            }
        }
        is PartData.FileItem -> {
            builder.fileName = part.originalFileName
            builder.fileBytes = part.streamProvider().readBytes()
        }
        else -> {}
    }
}

internal suspend fun extractMultipartData(call: ApplicationCall): MultipartPayload {
    val builder = MultipartBuilder()
    val multipart = call.receiveMultipart()
    multipart.forEachPart { part ->
        processPart(part, builder)
        part.dispose()
    }
    return MultipartPayload(builder.url, builder.fileBytes, builder.fileName)
}
