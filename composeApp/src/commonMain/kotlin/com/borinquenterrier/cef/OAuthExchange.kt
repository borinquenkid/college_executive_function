package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Int
)

class OAuthExchange(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchangeCodeForTokens(
        code: String,
        clientId: String,
        clientSecret: String?,
        redirectUri: String
    ): TokenResponse {
        return performTokenExchange(
            parameters {
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
                if (clientSecret != null) {
                    append("client_secret", clientSecret)
                }
            }
        )
    }

    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String?
    ): TokenResponse {
        return performTokenExchange(
            parameters {
                append("refresh_token", refreshToken)
                append("client_id", clientId)
                append("grant_type", "refresh_token")
                if (clientSecret != null) {
                    append("client_secret", clientSecret)
                }
            }
        )
    }

    private suspend fun performTokenExchange(params: Parameters): TokenResponse {
        val response = httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = params
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to exchange code/refresh token: ${response.bodyAsText()}")
        }

        return response.body<TokenResponse>()
    }
}
