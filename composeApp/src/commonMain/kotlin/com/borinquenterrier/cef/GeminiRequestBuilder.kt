package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

/**
 * Builds and executes HTTP requests to Gemini API with appropriate authentication.
 * Handles URL construction and request headers.
 */
class GeminiRequestBuilder(
    private val client: HttpClient,
    private val apiKey: String?,
    private val accessToken: String?
) {
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    fun buildPostUrl(modelName: String): String {
        val baseUrl = "$apiUrl/$modelName:generateContent"
        return if (apiKey != null) "$baseUrl?key=$apiKey" else baseUrl
    }

    suspend fun postToModel(modelName: String, body: JsonObject): HttpResponse {
        val url = buildPostUrl(modelName)
        return client.post(url) {
            contentType(ContentType.Application.Json)
            if (apiKey == null && accessToken != null) {
                header("Authorization", "Bearer $accessToken")
            }
            setBody(body)
        }
    }

    fun hasApiKey(): Boolean = apiKey != null
    fun hasAccessToken(): Boolean = accessToken != null
}
