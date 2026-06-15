package com.borinquenterrier.cef

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.file.Paths
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.borinquenterrier.cef.getAppDirectory

/**
 * JVM Implementation using the Local Server Flow.
 * Note: This flow REQUIRES opening a web browser to complete the Google login.
 */
actual class GoogleAuthService actual constructor(
    private val settings: Settings,
    private val logger: Logger?
) {

    private val tag = "GoogleAuth"
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val scopes = listOf(
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/drive.readonly"
    )

    actual suspend fun login(): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            logger?.d(tag, "Preparing Google Login flow...")

            val (credential, accessToken) = signInRetryingOnStaleCredential(
                authorize = ::authorize,
                obtainAccessToken = ::obtainAccessToken,
                onStaleCredential = { e ->
                    logger?.i(tag, "Stored Google credentials look stale (${e.message}); clearing and retrying with a fresh sign-in...")
                    logout()
                }
            )

            logger?.d(tag, "Google Login Successful!")
            Pair(accessToken, credential.refreshToken)
        } catch (e: Exception) {
            val errorMsg = "Login failed: ${e.message}"
            logger?.e(tag, errorMsg, e)
            throw Exception(errorMsg)
        }
    }

    /** Runs the (browser-based, when needed) authorization flow and returns the resulting credential. */
    private fun authorize(): Credential {
        val flow = buildFlow()
        logger?.i(tag, "Attempting to open your default browser for sign-in on an ephemeral port...")
        val receiver = LocalServerReceiver.Builder()
            .build() // Default port is -1, which uses a random free port allocated by the OS
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private fun obtainAccessToken(credential: Credential): String =
    // A credential loaded from a prior session can come back with a null accessToken
    // if only its refreshToken was persisted — authorize() accepts such a credential
        // as "valid" without refreshing it.
        resolveAccessToken(credential.accessToken) {
            credential.refreshToken()
            credential.accessToken
        }

    /**
     * Returns [currentAccessToken] if present; otherwise calls [refresh] once to obtain a
     * fresh one. Throws if both are null — this is the only path that hands a token to
     * [GoogleTokenRepository.saveTokens], whose `accessToken` parameter is non-null, so a
     * null here must become a clear error rather than a cryptic Kotlin null-check crash.
     */
    internal fun resolveAccessToken(currentAccessToken: String?, refresh: () -> String?): String {
        return currentAccessToken
            ?: refresh()
            ?: throw Exception("Google sign-in succeeded but no access token could be obtained. Please disconnect and reconnect your Google account.")
    }

    /**
     * Sign-in orchestration, parameterized so it's testable without real Google OAuth
     * machinery: produce a credential and resolve its access token; if that fails (e.g. a
     * revoked refresh token surfacing as "401 Unauthorized" from Google's token endpoint),
     * notify [onStaleCredential] (which is expected to clear the stale local session) and
     * try exactly once more with a fresh credential. The second failure propagates as-is.
     */
    internal fun <C> signInRetryingOnStaleCredential(
        authorize: () -> C,
        obtainAccessToken: (C) -> String,
        onStaleCredential: (Exception) -> Unit
    ): Pair<C, String> {
        val firstCredential = authorize()
        val accessToken = try {
            obtainAccessToken(firstCredential)
        } catch (e: Exception) {
            onStaleCredential(e)
            val freshCredential = authorize()
            return Pair(freshCredential, obtainAccessToken(freshCredential))
        }
        return Pair(firstCredential, accessToken)
    }

    actual suspend fun refreshAccessToken(refreshToken: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val flow = buildFlow()
                val credential = flow.createAndStoreCredential(
                    GoogleTokenResponse().setRefreshToken(refreshToken),
                    "user"
                )
                if (credential.refreshToken()) {
                    credential.accessToken
                } else {
                    null
                }
            } catch (e: Exception) {
                logger?.e(tag, "Automatic token refresh failed", e)
                null
            }
        }

    actual fun logout() {
        try {
            val flow = buildFlow()
            flow.credentialDataStore.delete("user")
            logger?.i(tag, "Local session cleared.")
        } catch (e: Exception) {
            logger?.e(tag, "Logout error", e)
        }
    }

    private fun loadEnvFile(): Map<String, String> {
        val envMap = mutableMapOf<String, String>()
        try {
            val envFile = File(".env")
            if (envFile.exists()) {
                envFile.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", limit = 2)
                        if (parts.size == 2) {
                            envMap[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger?.e(tag, "Failed to load .env file", e)
        }
        return envMap
    }

    internal fun buildFlow(): GoogleAuthorizationCodeFlow {
        val bypassEnv = System.getProperty("CEF_BYPASS_ENV_SECRETS") == "true"

        // 1. Try JVM system properties first, then OS env vars (env vars skipped in tests via CEF_BYPASS_ENV_SECRETS)
        var clientId = System.getProperty("GOOGLE_CLIENT_ID")
            ?: if (bypassEnv) null else System.getenv("GOOGLE_CLIENT_ID")
        var clientSecret = System.getProperty("GOOGLE_CLIENT_SECRET")
            ?: if (bypassEnv) null else System.getenv("GOOGLE_CLIENT_SECRET")

        // 2. Try build-time injected secrets (unless bypassed in tests)
        if (System.getProperty("CEF_BYPASS_BUILD_SECRETS") != "true") {
            if (clientId.isNullOrBlank()) {
                clientId = BuildSecrets.GOOGLE_CLIENT_ID
            }
            if (clientSecret.isNullOrBlank()) {
                clientSecret = BuildSecrets.GOOGLE_CLIENT_SECRET
            }
        }

        // 3. Fallback to parsing the local .env file
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            val envMap = loadEnvFile()
            if (clientId.isNullOrBlank()) {
                clientId = envMap["GOOGLE_CLIENT_ID"]
            }
            if (clientSecret.isNullOrBlank()) {
                clientSecret = envMap["GOOGLE_CLIENT_SECRET"]
            }
        }

        val clientSecrets = if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
            val jsonString = """
                {
                  "installed": {
                    "client_id": "$clientId",
                    "client_secret": "$clientSecret",
                    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                    "token_uri": "https://oauth2.googleapis.com/token"
                  }
                }
            """.trimIndent()
            GoogleClientSecrets.load(jsonFactory, StringReader(jsonString))
        } else {
            // 3. Fallback to client_secret.json file
            val envPath = System.getProperty("CEF_GOOGLE_CLIENT_SECRET_PATH")
                ?: System.getenv("CEF_GOOGLE_CLIENT_SECRET_PATH")
            val defaultPath = File(getAppDirectory(), "client_secret.json").absolutePath
            val secretPath = envPath ?: defaultPath

            val secretFile = File(secretPath)
            if (!secretFile.exists()) {
                throw IllegalStateException(
                    "Google Client ID/Secret not found in environment variables or .env file, " +
                            "and client_secret.json not found at $secretPath. Please configure it."
                )
            }
            GoogleClientSecrets.load(jsonFactory, InputStreamReader(FileInputStream(secretFile)))
        }

        return GoogleAuthorizationCodeFlow.Builder(
            transport,
            jsonFactory,
            clientSecrets,
            scopes
        ).setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
            .setAccessType("offline")
            .build()
    }
}
