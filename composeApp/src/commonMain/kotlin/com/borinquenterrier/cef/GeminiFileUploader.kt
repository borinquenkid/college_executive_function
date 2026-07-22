package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Uploads a document to the Gemini Files API for inputs too large for an inline base64 part
 * (~20 MB cap) — e.g. a big scanned/image PDF. Returns the file URI once the file is ACTIVE, or
 * null on any failure so the caller degrades gracefully (no events instead of a crash).
 */
class GeminiFileUploader(
    private val client: HttpClient,
    private val apiKey: String?,
    private val logger: Logger? = null,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val maxPollAttempts: Int = 10
) {
    private val base = "https://generativelanguage.googleapis.com"
    private val tag = "GeminiFileUploader"
    private val json = Json { ignoreUnknownKeys = true }

    private fun key() = if (apiKey != null) "?key=$apiKey" else ""

    /** Uploads [bytes] and returns the file URI once ACTIVE, else null. */
    suspend fun uploadAndAwait(bytes: ByteArray, mimeType: String): String? = try {
        val uploadBody = client.post("$base/upload/v1beta/files${key()}") {
            header("X-Goog-Upload-Protocol", "raw")
            header("Content-Type", mimeType)
            setBody(bytes)
        }.let { if (it.status.isSuccess()) it.bodyAsText() else null }

        val file = uploadBody?.let { json.parseToJsonElement(it).jsonObject["file"]?.jsonObject }
        val name = file?.get("name")?.jsonPrimitive?.content
        val uri = file?.get("uri")?.jsonPrimitive?.content
        val initialState = file?.get("state")?.jsonPrimitive?.content

        if (name == null || uri == null) {
            logger?.e(tag, "Files API upload returned no file uri")
            null
        } else {
            awaitActive(name, uri, initialState)
        }
    } catch (e: Exception) {
        logger?.e(tag, "Files API upload failed: ${e.message}")
        null
    }

    private suspend fun awaitActive(name: String, uri: String, initialState: String?): String? {
        if (initialState == "ACTIVE") return uri
        repeat(maxPollAttempts) {
            delayFn(1000)
            // A transient network error or a non-2xx poll response (e.g. a 5xx blip) shouldn't
            // permanently fail an upload that has maxPollAttempts left to give — treat it the
            // same as "still PROCESSING" and let the bounded retry loop keep going, instead of
            // aborting on the first hiccup.
            val response = try {
                client.get("$base/v1beta/$name${key()}")
            } catch (e: Exception) {
                logger?.e(tag, "Files API poll failed: ${e.message} — retrying")
                return@repeat
            }
            if (!response.status.isSuccess()) {
                logger?.e(tag, "Files API poll returned ${response.status} for $name — retrying")
                return@repeat
            }
            val state = try {
                json.parseToJsonElement(response.bodyAsText()).jsonObject["state"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                logger?.e(tag, "Files API poll response unparsable: ${e.message} — retrying")
                return@repeat
            }
            when (state) {
                "ACTIVE" -> return uri
                "FAILED" -> { logger?.e(tag, "Files API processing FAILED for $name"); return null }
                else -> {} // still PROCESSING — keep polling
            }
        }
        logger?.e(tag, "Files API file $name not ACTIVE after $maxPollAttempts polls")
        return null
    }
}
