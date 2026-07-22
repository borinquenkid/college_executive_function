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
                val newToken = try {
                    authService.refreshAccessToken(refreshToken)
                } catch (refreshError: Exception) {
                    // A thrown exception means the refresh attempt itself failed transiently
                    // (timeout/no connection/5xx) — the refresh token may still be fine. Surface
                    // the original 401 (so this sync attempt still fails, correctly) without the
                    // misleading "session expired, please reconnect" message, which should only
                    // fire when the grant is genuinely dead (see GoogleAccountFlow for the same
                    // distinction on the startup-check path).
                    println("[GoogleTokenService] Token refresh attempt failed transiently: ${refreshError.message}")
                    throw e
                }
                if (newToken == null) {
                    onAuthExpired?.invoke(sessionExpiredMessage)
                    throw e
                }
                tokenRepository.saveTokens(newToken, refreshToken)
                try {
                    block(newToken)
                } catch (retryEx: GoogleApiException) {
                    if (retryEx.statusCode == 401) {
                        println("[GoogleTokenService] Retry after token refresh still got 401, session expired: ${retryEx.message}")
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
