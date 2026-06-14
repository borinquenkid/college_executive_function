package com.borinquenterrier.cef

/**
 * Manages Google authentication state and linking/unlinking logic.
 * Coordinates auth service with token persistence.
 */
class GoogleAuthManager(
    private val authService: GoogleAuthService,
    private val tokenRepository: GoogleTokenRepository,
    private val logger: Logger
) {
    private val tag = "GoogleAuthManager"

    suspend fun loginAndLink(): Boolean {
        return try {
            val (accessToken, refreshToken) = authService.login()
            tokenRepository.saveTokens(accessToken, refreshToken)
            logger.d(tag, "Google login successful")
            true
        } catch (e: Exception) {
            logger.e(tag, "Google login failed", e)
            false
        }
    }

    fun isLinked(): Boolean {
        return tokenRepository.hasTokens()
    }
}
