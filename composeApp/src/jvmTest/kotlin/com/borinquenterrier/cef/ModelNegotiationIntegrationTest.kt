package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

/**
 * Release gate: verifies the OAuth client credentials baked into BuildSecrets are accepted
 * by Google's token endpoint. Fails hard if GOOGLE_REFRESH_TOKEN is missing so that a
 * misconfigured CI job is caught here rather than shipping a broken sign-in flow.
 *
 * Required secrets (OS env var or .env):
 *   GOOGLE_REFRESH_TOKEN — paired with the GOOGLE_CLIENT_ID/SECRET that ship in the app
 */
class ModelNegotiationIntegrationTest : FunSpec({

    test("Google OAuth: app client credentials are accepted by Google token endpoint").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        if (isTestProfile()) {
            println("SKIPPING Google OAuth: Disabled in test/offline profile.")
            return@config
        }
        val creds = resolveLiveCredentials()
        val settings = MapSettings()
        val authService = GoogleAuthService(settings, AppEnv())
        val newToken = runBlocking { authService.refreshAccessToken(creds.refreshToken) }
        newToken.shouldNotBeBlank()
    }
})
