package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A Finite State Automaton (FSA) that manages the Google Account connection lifecycle.
 */
class GoogleAccountFlow(
    private val authService: GoogleAuthService,
    private val tokenRepository: GoogleTokenRepository,
    private val logger: Logger? = null
) {
    private val tag = "GoogleAccountFlow"
    lateinit var driveService: GoogleDriveService

    private val _state = MutableStateFlow<GoogleConnectionState>(
        if (tokenRepository.hasTokens()) GoogleConnectionState.Linked else GoogleConnectionState.Unlinked
    )
    val state: StateFlow<GoogleConnectionState> = _state.asStateFlow()

    suspend fun connect() {
        if (_state.value is GoogleConnectionState.Connecting) return

        _state.value = GoogleConnectionState.Connecting
        try {
            logger?.d(tag, "Transition: Unlinked -> Connecting")
            authService.logout()
            val result = authService.login()

            logger?.d(tag, "Saving tokens...")
            tokenRepository.saveTokens(result.first, result.second)

            logger?.d(tag, "Validating Drive access...")
            val isValid = driveService.validateConnection(result.first)

            if (isValid) {
                logger?.d(tag, "Transition: Connecting -> Linked")
                _state.value = GoogleConnectionState.Linked
            } else {
                logger?.e(tag, "Transition: Connecting -> Error (Drive Access Denied)")
                tokenRepository.clearTokens()
                _state.value = GoogleConnectionState.Error(
                    "Connected to Google, but Drive access failed. Please ensure you checked the permission box in the browser.",
                    canRetry = true
                )
            }
        } catch (e: Exception) {
            logger?.e(tag, "Transition: Connecting -> Error (${e.message})", e)
            _state.value = GoogleConnectionState.Error(e.message ?: "Unknown login error")
        }
    }

    fun disconnect() {
        logger?.d(tag, "Transition: * -> Unlinked")
        authService.logout()
        tokenRepository.clearTokens()
        _state.value = GoogleConnectionState.Unlinked
    }

    /**
     * Call this when a background operation (like listFiles) fails due to auth.
     */
    fun reportAuthError(message: String) {
        logger?.e(tag, "Transition: Linked -> Error ($message)")
        _state.value = GoogleConnectionState.Error(message)
    }
}
