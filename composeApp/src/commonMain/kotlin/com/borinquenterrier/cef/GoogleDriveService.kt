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
class GoogleDriveService(
    private val httpClient: HttpClient,
    private val tokenRepository: GoogleTokenRepository,
    private val authService: GoogleAuthService,
    private val onAuthError: ((String) -> Unit)? = null,
    private val logger: Logger? = null
) {

    private val baseUrl = "https://www.googleapis.com/drive/v3"

    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val currentToken =
            tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")
        logger?.d(
            "GoogleDriveService",
            "Attempting request with token: ${currentToken.take(10)}..."
        )

        return try {
            block(currentToken)
        } catch (e: Exception) {
            val isUnauthorized =
                e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true
            if (isUnauthorized) {
                logger?.d("GoogleDriveService", "Received 401, attempting token refresh.")
                val refreshToken = tokenRepository.getRefreshToken() ?: run {
                    onAuthError?.invoke("Session expired. Please reconnect.")
                    throw Exception("No refresh token available for 401 recovery")
                }

                try {
                    val newToken = authService.refreshAccessToken(refreshToken)
                        ?: throw Exception("Token refresh failed")
                    logger?.d(
                        "GoogleDriveService",
                        "Successfully refreshed token: ${newToken.take(10)}..."
                    )

                    tokenRepository.saveTokens(newToken, refreshToken)
                    block(newToken)
                } catch (refreshError: Exception) {
                    onAuthError?.invoke("Google connection lost: ${refreshError.message}")
                    throw refreshError
                }
            } else {
                logger?.e("GoogleDriveService", "API call failed with: ${e.message}")
                throw e
            }
        }
    }

    sealed interface ValidationResult {
        data object Success : ValidationResult
        data object InvalidCredentials : ValidationResult
        data class NetworkError(val message: String) : ValidationResult
    }

    suspend fun validateConnectionResult(accessToken: String): ValidationResult {
        return try {
            val response = httpClient.get("$baseUrl/files") {
                header("Authorization", "Bearer $accessToken")
                parameter("pageSize", 1)
            }
            if (response.status.isSuccess()) {
                ValidationResult.Success
            } else {
                val errorBody = response.bodyAsText()
                println("[GoogleDriveService] Validation status is not success: ${response.status}. Body: $errorBody")
                ValidationResult.InvalidCredentials
            }
        } catch (e: Exception) {
            println("[GoogleDriveService] Validation threw exception: ${e.message}")
            e.printStackTrace()
            ValidationResult.NetworkError(e.message ?: "Network error")
        }
    }

    /**
     * Verifies that the connection is working by fetching a single file metadata.
     * Returns true if successful, false otherwise.
     */
    suspend fun validateConnection(accessToken: String): Boolean {
        return validateConnectionResult(accessToken) is ValidationResult.Success
    }

    /**
     * Lists files in the user's Google Drive.
     * Optionally filters by [query] (e.g., "mimeType = 'application/pdf'").
     */
    suspend fun listFiles(query: String? = null): List<DriveFile> = withToken { token ->
        logger?.d(
            "GoogleDriveService",
            "Attempting listFiles with Authorization: Bearer ${token.take(10)}..."
        )
        val response = httpClient.get("$baseUrl/files") {
            header("Authorization", "Bearer $token")
            query?.let { parameter("q", it) }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            logger?.e("GoogleDriveService", "Drive API error: $errorBody")
            throw Exception("Google Drive API Error (${response.status}): $errorBody")
        }

        response.body<DriveFileListResponse>().files
    }

    /**
     * Fetches the text content of a file.
     * For Google Docs, it exports them as plain text.
     */
    suspend fun getFileContent(fileId: String, mimeType: String): String = withToken { token ->
        val response = if (mimeType == "application/vnd.google-apps.document") {
            // Export Google Doc as plain text
            httpClient.get("$baseUrl/files/$fileId/export") {
                header("Authorization", "Bearer $token")
                parameter("mimeType", "text/plain")
            }
        } else {
            // Download binary file (PDF/Text) content
            httpClient.get("$baseUrl/files/$fileId") {
                header("Authorization", "Bearer $token")
                parameter("alt", "media")
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("Google Drive API Error (${response.status}): $errorBody")
        }

        response.body<String>()
    }
}
