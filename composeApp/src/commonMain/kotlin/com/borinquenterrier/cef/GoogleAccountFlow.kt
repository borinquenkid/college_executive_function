package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles the logic for connecting and disconnecting Google accounts.
 * Decoupled from the UI to allow for headless testing.
 */
class GoogleAccountFlow(
    private val authService: GoogleAuthService,
    private val tokenRepository: GoogleTokenRepository,
    private val driveService: GoogleDriveService
) {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    suspend fun connect() {
        _isBusy.value = true
        _error.value = null
        try {
            println("[GoogleAccountFlow] Starting login...")
            val result = authService.login()
            println("[GoogleAccountFlow] Login success, saving tokens...")
            tokenRepository.saveTokens(result.first, result.second)
            
            println("[GoogleAccountFlow] Validating Drive access...")
            val isValid = driveService.validateConnection(result.first)
            if (!isValid) {
                tokenRepository.clearTokens()
                _error.value = "Connected to Google, but Drive access failed. Please ensure you checked the permission box in the browser."
            }
        } catch (e: Exception) {
            println("[GoogleAccountFlow] Login error: ${e.message}")
            _error.value = e.message ?: "Unknown login error"
        } finally {
            _isBusy.value = false
        }
    }

    fun disconnect() {
        println("[GoogleAccountFlow] Disconnecting...")
        authService.logout()
        tokenRepository.clearTokens()
        _error.value = null
    }
}
