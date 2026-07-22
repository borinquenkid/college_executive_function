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
    private val calendarSyncService: GoogleCalendarSyncService
) {
    private sealed interface ValidationResult {
        data object Success : ValidationResult
        data object InvalidCredentials : ValidationResult
        data class NetworkError(val message: String) : ValidationResult
    }

    // Pings the Calendar API (the only Google scope this app still requests) purely to confirm
    // the just-granted/just-loaded token actually works — same role the old Drive-based
    // validateConnection ping played before the drive.readonly scope was dropped.
    private suspend fun validateConnection(): ValidationResult {
        return try {
            calendarSyncService.listCalendars()
            ValidationResult.Success
        } catch (e: GoogleApiException) {
            println("[GoogleAccountFlow] validateConnection failed: ${e.message}")
            if (e.statusCode == 401) ValidationResult.InvalidCredentials
            else ValidationResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            println("[GoogleAccountFlow] validateConnection failed: ${e.message}")
            ValidationResult.NetworkError(e.message ?: "Network error")
        }
    }

    private val _state = MutableStateFlow<GoogleConnectionState>(
        if (tokenRepository.hasTokens()) GoogleConnectionState.Linked else GoogleConnectionState.Unlinked
    )
    val state: StateFlow<GoogleConnectionState> = _state.asStateFlow()

    suspend fun connect() {
        if (_state.value is GoogleConnectionState.Connecting) return

        _state.value = GoogleConnectionState.Connecting
        try {
            println("[GoogleAccountFlow] Transition: Unlinked -> Connecting")
            authService.logout()
            val result = authService.login()

            println("[GoogleAccountFlow] Saving tokens...")
            tokenRepository.saveTokens(result.first, result.second)

            println("[GoogleAccountFlow] Validating Calendar access...")
            val isValid = validateConnection() is ValidationResult.Success

            if (isValid) {
                println("[GoogleAccountFlow] Transition: Connecting -> Linked")
                _state.value = GoogleConnectionState.Linked
            } else {
                println("[GoogleAccountFlow] Transition: Connecting -> Error (Calendar Access Denied)")
                tokenRepository.clearTokens()
                _state.value = GoogleConnectionState.Error(
                    "Connected to Google, but Calendar access failed. Please ensure you checked the permission box in the browser.",
                    canRetry = true
                )
            }
        } catch (e: Exception) {
            println("[GoogleAccountFlow] Transition: Connecting -> Error (${e.message})")
            _state.value = GoogleConnectionState.Error(e.message ?: "Unknown login error")
        }
    }

    fun disconnect() {
        println("[GoogleAccountFlow] Transition: * -> Unlinked")
        authService.logout()
        tokenRepository.clearTokens()
        _state.value = GoogleConnectionState.Unlinked
    }

    /**
     * Call this when a background operation (like listFiles) fails due to auth.
     */
    fun reportAuthError(message: String) {
        println("[GoogleAccountFlow] Transition: Linked -> Error ($message)")
        _state.value = GoogleConnectionState.Error(message)
    }

    suspend fun checkConnectionOnStartup() {
        tokenRepository.getAccessToken() ?: return

        when (val validation = validateConnection()) {
            is ValidationResult.Success -> {
                _state.value = GoogleConnectionState.Linked
            }
            is ValidationResult.NetworkError -> {
                println("[GoogleAccountFlow] Startup validation failed with network error: ${validation.message}. Retaining Linked state.")
            }
            is ValidationResult.InvalidCredentials -> {
                println("[GoogleAccountFlow] Startup validation: invalid access token. Attempting refresh...")
                handleInvalidAccessToken()
            }
        }
    }

    private suspend fun handleInvalidAccessToken() {
        val refreshToken = tokenRepository.getRefreshToken()
        if (refreshToken == null) {
            println("[GoogleAccountFlow] Startup validation: no refresh token. Disconnecting.")
            disconnect()
            return
        }
        
        val newAccessToken = try {
            authService.refreshAccessToken(refreshToken)
        } catch (e: Exception) {
            // A thrown exception here means the refresh attempt itself failed for a transient
            // reason (timeout, no connection, 5xx) — the refresh token may still be perfectly
            // valid. Only an explicit null return (see GoogleAuthService's platform actuals,
            // which now only swallow a genuinely-invalid/revoked grant to null) means the grant
            // is actually dead. Disconnecting the account over a dropped connection would be
            // the same class of bug as the offline case validateConnection() already guards
            // against above — don't repeat it one level down.
            println("[GoogleAccountFlow] Startup validation: refresh attempt failed transiently (${e.message}). Retaining Linked state.")
            return
        }

        if (newAccessToken != null) {
            println("[GoogleAccountFlow] Startup validation: successfully refreshed token.")
            tokenRepository.saveTokens(newAccessToken, refreshToken)
            _state.value = GoogleConnectionState.Linked
        } else {
            println("[GoogleAccountFlow] Startup validation: refresh token invalid/expired. Disconnecting.")
            disconnect()
        }
    }
}
