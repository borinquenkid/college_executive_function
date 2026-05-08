package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

actual class GoogleAuthService actual constructor(private val settings: Settings) {
    actual suspend fun login(): Pair<String, String?> {
        // TODO: Implement Google Sign-In for iOS using native SDK
        return Pair("mock-ios-access-token", "mock-ios-refresh-token")
    }

    actual suspend fun refreshAccessToken(refreshToken: String): String? {
        return "mock-refreshed-token"
    }

    actual fun logout() {
        // TODO: Implement native logout
    }
}
