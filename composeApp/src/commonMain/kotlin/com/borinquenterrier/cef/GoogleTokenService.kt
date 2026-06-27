package com.borinquenterrier.cef

class GoogleTokenService(
    private val tokenRepository: GoogleTokenRepository,
    private val authService: GoogleAuthService,
    private val onAuthExpired: ((String) -> Unit)? = null
) {
    private val sessionExpiredMessage = "Google session expired. Please reconnect your Google account."

    suspend fun <T> withToken(block: suspend (String) -> T): T {
        val currentToken = tokenRepository.getAccessToken()
            ?: throw Exception("Not authenticated with Google")
        return try {
            block(currentToken)
        } catch (e: GoogleApiException) {
            if (e.statusCode == 401) {
                val refreshToken = tokenRepository.getRefreshToken()
                if (refreshToken == null) {
                    onAuthExpired?.invoke(sessionExpiredMessage)
                    throw e
                }
                val newToken = authService.refreshAccessToken(refreshToken)
                if (newToken == null) {
                    onAuthExpired?.invoke(sessionExpiredMessage)
                    throw e
                }
                tokenRepository.saveTokens(newToken, refreshToken)
                try {
                    block(newToken)
                } catch (retryEx: GoogleApiException) {
                    if (retryEx.statusCode == 401) {
                        onAuthExpired?.invoke(sessionExpiredMessage)
                        throw Exception(sessionExpiredMessage, retryEx)
                    }
                    throw retryEx
                }
            } else {
                throw e
            }
        }
    }
}
