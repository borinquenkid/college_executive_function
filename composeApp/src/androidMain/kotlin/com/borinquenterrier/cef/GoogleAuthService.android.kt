package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

actual class GoogleAuthService actual constructor(private val settings: Settings) {
    actual suspend fun login(): Pair<String, String?> {
        // TODO: Implement Google Sign-In for Android using Credential Manager or Google Sign-In SDK
        return Pair("mock-android-access-token", "mock-android-refresh-token")
    }

    actual suspend fun refreshAccessToken(refreshToken: String): String? {
        return "mock-refreshed-token"
    }
}
