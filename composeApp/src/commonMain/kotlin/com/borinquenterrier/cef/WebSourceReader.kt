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
            client.get(url).body<String>()
        } catch (e: Exception) {
            // In a real app, we would want more sophisticated error handling
            e.printStackTrace()
            "Error: Could not read content from URL. ${e.message}"
        }
    }
}
