package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import okio.FileSystem
import okio.Path.Companion.toPath

class ModelManager(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val logger: Logger? = null
) {
    private val tag = "ModelManager"
    private val modelUrl = "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf"
    private val modelFileName = "Qwen3.5-9B-Q4_K_M.gguf"

    fun getModelFile(): okio.Path {
        return basePath.toPath() / modelFileName
    }

    fun isModelDownloaded(): Boolean {
        return try {
            FileSystem.SYSTEM.exists(getModelFile())
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadModel(): Flow<DownloadProgress> = flow {
        val destination = getModelFile()
        
        // Ensure directory exists
        val parent = destination.parent
        if (parent != null && !FileSystem.SYSTEM.exists(parent)) {
            FileSystem.SYSTEM.createDirectories(parent)
        }

        logger?.d(tag, "Starting streaming download from $modelUrl to $destination")

        httpClient.prepareGet(modelUrl).execute { response ->
            if (!response.status.isSuccess()) {
                throw Exception("Failed to download model: ${response.status}")
            }
            
            val channel: ByteReadChannel = response.bodyAsChannel()
            val contentLength = response.contentLength() ?: -1L
            var totalBytesRead = 0L

            withContext(Dispatchers.Default) {
                FileSystem.SYSTEM.write(destination) {
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(1024 * 64) // 64KB chunks
                        while (!packet.exhausted()) {
                            val bytes = packet.readByteArray()
                            write(bytes)
                            totalBytesRead += bytes.size
                            
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength
                                // Emit progress every 1MB to avoid flooding the flow
                                if (totalBytesRead % (1024 * 1024) == 0L) {
                                    // Note: flow collector handles emission
                                }
                            }
                        }
                    }
                }
            }
        }
        
        logger?.d(tag, "Download complete")
        emit(DownloadProgress(1f, true))
    }
}

data class DownloadProgress(val progress: Float, val isDone: Boolean)
