package com.borinquenterrier.cef

class GoogleTokenService(
    private val tokenRepository: GoogleTokenRepository,
    private val authService: GoogleAuthService
) {
    suspend fun <T> withToken(block: suspend (String) -> T): T {
        val currentToken = tokenRepository.getAccessToken()
            ?: throw Exception("Not authenticated with Google")
        return try {
            block(currentToken)
        } catch (e: GoogleApiException) {
            if (e.statusCode == 401) {
                val refreshToken = tokenRepository.getRefreshToken() ?: throw e
                val newToken = authService.refreshAccessToken(refreshToken) ?: throw e
                tokenRepository.saveTokens(newToken, refreshToken)
                try {
                    block(newToken)
                } catch (retryEx: GoogleApiException) {
                    if (retryEx.statusCode == 401) {
                        throw Exception("Google session expired. Please reconnect your Google account.", retryEx)
                    }
                    throw retryEx
                }
            } else {
                throw e
            }
        }
    }
}
