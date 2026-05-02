package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Google Drive File metadata model.
 */
@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String
)

@Serializable
data class DriveFileListResponse(
    val files: List<DriveFile>
)

/**
 * KMP-compatible service to interact with the Google Drive API.
 */
class GoogleDriveService(private val httpClient: HttpClient) {

    private val baseUrl = "https://www.googleapis.com/drive/v3"

    /**
     * Verifies that the connection is working by fetching a single file metadata.
     * Returns true if successful, false otherwise.
     */
    suspend fun validateConnection(accessToken: String): Boolean {
        return try {
            httpClient.get("$baseUrl/files") {
                header("Authorization", "Bearer $accessToken")
                parameter("pageSize", 1)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Lists files in the user's Google Drive.
     * Optionally filters by [query] (e.g., "mimeType = 'application/pdf'").
     */
    suspend fun listFiles(accessToken: String, query: String? = null): List<DriveFile> {
        val response = httpClient.get("$baseUrl/files") {
            header("Authorization", "Bearer $accessToken")
            query?.let { parameter("q", it) }
        }
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("Google Drive API Error (${response.status}): $errorBody")
        }
        
        return response.body<DriveFileListResponse>().files
    }

    /**
     * Fetches the text content of a file.
     * For Google Docs, it exports them as plain text.
     */
    suspend fun getFileContent(accessToken: String, fileId: String, mimeType: String): String {
        val response = if (mimeType == "application/vnd.google-apps.document") {
            // Export Google Doc as plain text
            httpClient.get("$baseUrl/files/$fileId/export") {
                header("Authorization", "Bearer $accessToken")
                parameter("mimeType", "text/plain")
            }
        } else {
            // Download binary file (PDF/Text) content
            httpClient.get("$baseUrl/files/$fileId") {
                header("Authorization", "Bearer $accessToken")
                parameter("alt", "media")
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("Google Drive API Error (${response.status}): $errorBody")
        }

        return response.body<String>()
    }
}
