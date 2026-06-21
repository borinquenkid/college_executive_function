package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

/**
 * Shared repository to store and retrieve OAuth tokens.
 */
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared repository to store and retrieve OAuth tokens.
 */
class GoogleTokenRepository(private val settings: Settings) {
    private val accessTokenKey = "GOOGLE_ACCESS_TOKEN"
    private val refreshTokenKey = "GOOGLE_REFRESH_TOKEN"

    private val _isLinked = MutableStateFlow(hasTokens())
    val isLinked: StateFlow<Boolean> = _isLinked.asStateFlow()

    fun saveTokens(accessToken: String, refreshToken: String?) {
        settings.putString(accessTokenKey, accessToken)
        if (refreshToken != null) {
            settings.putString(refreshTokenKey, refreshToken)
        }
        _isLinked.value = true
    }

    fun clearTokens() {
        settings.remove(accessTokenKey)
        settings.remove(refreshTokenKey)
        _isLinked.value = false
    }

    fun hasTokens(): Boolean = settings.getString(accessTokenKey, "").isNotBlank()

    fun getAccessToken(): String? = settings.getString(accessTokenKey, "")
        .takeIf { it.isNotEmpty() }

    fun getRefreshToken(): String? = settings.getString(refreshTokenKey, "")
        .takeIf { it.isNotEmpty() }
}

/**
 * Platform-specific service to handle the OAuth2 flow.
 */
expect class GoogleAuthService(settings: Settings, appEnv: AppEnv) {
    suspend fun login(): Pair<String, String?> // Returns AccessToken and optional RefreshToken
    suspend fun refreshAccessToken(refreshToken: String): String?
    fun logout()
}
