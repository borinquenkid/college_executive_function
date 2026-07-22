package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

class WebSourceReader(
    // Overridable so tests can inject a MockEngine-backed client instead of making real requests.
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        // Without this, a hung connection never throws — readBytesFromUrl/readTextFromUrl would
        // suspend indefinitely instead of failing, leaving source ingestion stuck forever.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }
) {

    suspend fun readTextFromUrl(url: String): String {
        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                return "Error loading content from URL: HTTP ${response.status}"
            }
            cleanHtml(response.body<String>())
        } catch (e: Exception) {
            "Error loading content from URL: ${e.message}"
        }
    }

    /** Raw response bytes — lets a URL that points at a PDF/ICS go through the normalizer's
     *  extractors. Throws on a non-2xx response instead of silently returning an error page's
     *  body as if it were the real file — the caller (IngestionAgent.addUrl) has nothing else
     *  that would ever notice the difference otherwise, and would mark the source as
     *  successfully ingested despite it being e.g. a 404 page. */
    suspend fun readBytesFromUrl(url: String): ByteArray {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw Exception("Failed to fetch $url: HTTP ${response.status}")
        }
        return response.body<ByteArray>()
    }

    fun cleanHtml(html: String): String {
        var text = html

        // 1. Remove scripts and styles content
        text = text.replace(
            Regex("<script[\\s\\S]*?>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE),
            ""
        )
        text =
            text.replace(Regex("<style[\\s\\S]*?>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

        // 2. Remove all HTML tags
        text = text.replace(Regex("<.*?>"), " ")

        // 3. Decode some basic entities (can be expanded)
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")

        // 4. Normalize whitespace
        return text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }
}

