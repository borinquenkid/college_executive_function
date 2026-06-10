package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.readByteArray
import okio.Path.Companion.toPath

class ModelManager(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val logger: Logger? = null
) {
    private val tag = "ModelManager"
    private val modelUrl =
        "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf"
    private val modelFileName = "Qwen3.5-9B-Q4_K_M.gguf"

    fun getModelFile(): okio.Path {
        return basePath.toPath() / modelFileName
    }

    fun isModelDownloaded(): Boolean {
        return try {
            getFileSystem().exists(getModelFile())
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadModel(): Flow<DownloadProgress> = flow {
        val destination = getModelFile()
        val fileSystem = getFileSystem()

        // Ensure directory exists
        val parent = destination.parent
        if (parent != null && !fileSystem.exists(parent)) {
            fileSystem.createDirectories(parent)
        }

        logger?.d(tag, "Starting streaming download from $modelUrl to $destination")

        httpClient.prepareGet(modelUrl).execute { response ->
            if (!response.status.isSuccess()) {
                throw Exception("Failed to download model: ${response.status}")
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            val contentLength = response.contentLength() ?: -1L
            var totalBytesRead = 0L

            fileSystem.write(destination) {
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(1024 * 64) // 64KB chunks
                    while (!packet.exhausted()) {
                        val bytes = packet.readByteArray()
                        write(bytes)
                        totalBytesRead += bytes.size
                    }
                    // Emit intermediate progress after each chunk
                    val fraction = if (contentLength > 0L) {
                        (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f // unknown length — report 0 until done
                    }
                    emit(DownloadProgress(fraction, false))
                }
            }
        }

        logger?.d(tag, "Download complete")
        emit(DownloadProgress(1f, true))
    }.flowOn(Dispatchers.IO)
}

data class DownloadProgress(val progress: Float, val isDone: Boolean)
