package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

/**
 * Formal states for the Google Account connection lifecycle.
 */
@Serializable
sealed class GoogleConnectionState {
    @Serializable
    data object Unlinked : GoogleConnectionState()

    @Serializable
    data object Connecting : GoogleConnectionState()

    @Serializable
    data object Linked : GoogleConnectionState()

    @Serializable
    data class Error(val message: String, val canRetry: Boolean = true) : GoogleConnectionState()
}
