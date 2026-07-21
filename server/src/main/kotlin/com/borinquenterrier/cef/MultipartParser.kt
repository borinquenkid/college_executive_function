package com.borinquenterrier.cef

import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_UPLOAD_BYTES = 30L * 1024 * 1024

internal class MultipartTooLargeException(val maxBytes: Long) :
    Exception("Upload exceeds the $maxBytes byte limit")

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

/** Reads at most maxBytes without ever buffering more than that in memory — aborts as soon as the
 *  limit is crossed instead of reading the whole (possibly huge) body first and checking after. */
private fun InputStream.readBytesUpTo(maxBytes: Long): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) throw MultipartTooLargeException(maxBytes)
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
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
            builder.fileBytes = part.streamProvider().readBytesUpTo(MAX_UPLOAD_BYTES)
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
