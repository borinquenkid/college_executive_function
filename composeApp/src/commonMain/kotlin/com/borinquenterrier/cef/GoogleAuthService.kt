package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

/**
 * Shared repository to store and retrieve OAuth tokens.
 */
class GoogleTokenRepository(private val settings: Settings) {
    private val accessTokenKey = "GOOGLE_ACCESS_TOKEN"
    private val refreshTokenKey = "GOOGLE_REFRESH_TOKEN"

    fun saveTokens(accessToken: String, refreshToken: String?) {
        settings.putString(accessTokenKey, accessToken)
        if (refreshToken != null) {
            settings.putString(refreshTokenKey, refreshToken)
        }
    }

    fun getAccessToken(): String? = settings.getString(accessTokenKey, "")
        .takeIf { it.isNotEmpty() }
        
    fun getRefreshToken(): String? = settings.getString(refreshTokenKey, "")
        .takeIf { it.isNotEmpty() }

    fun hasTokens(): Boolean = getAccessToken() != null

    fun clearTokens() {
        settings.remove(accessTokenKey)
        settings.remove(refreshTokenKey)
    }
}
/**
 * Platform-specific service to handle the OAuth2 flow.
 */
expect class GoogleAuthService(settings: Settings) {
    suspend fun login(): Pair<String, String?> // Returns AccessToken and optional RefreshToken
    suspend fun refreshAccessToken(refreshToken: String): String?
}
