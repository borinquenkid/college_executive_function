package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json

class WebSourceReader {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun readTextFromUrl(url: String): String {
        return try {
            val rawBody = client.get(url).body<String>()
            cleanHtml(rawBody)
        } catch (e: Exception) {
            // In a real app, we would want more sophisticated error handling
            e.printStackTrace()
            "Error loading content from URL: ${e.message}"
        }
    }

    fun cleanHtml(html: String): String {
        var text = html

        // 1. Remove scripts and styles content
        text = text.replace(Regex("<script[\\s\\S]*?>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style[\\s\\S]*?>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

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

