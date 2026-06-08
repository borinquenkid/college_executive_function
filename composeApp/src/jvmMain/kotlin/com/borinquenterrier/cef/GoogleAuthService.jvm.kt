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
import java.io.InputStreamReader
import java.io.StringReader
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JVM Implementation using the Local Server Flow.
 * Note: This flow REQUIRES opening a web browser to complete the Google login.
 */
actual class GoogleAuthService actual constructor(private val settings: Settings) {

    private val tag = "GoogleAuth"
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val scopes = listOf(
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/drive.readonly"
    )
    private val credentialsDir = File(
        System.getProperty("CEF_CREDENTIALS_DIR") 
            ?: System.getenv("CEF_CREDENTIALS_DIR") 
            ?: System.getProperty("user.home"), 
        ".cef_credentials"
    )

    actual suspend fun login(): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            println("[$tag] Preparing Google Login flow...")

            val (credential, accessToken) = signInRetryingOnStaleCredential(
                authorize = ::authorize,
                obtainAccessToken = ::obtainAccessToken,
                onStaleCredential = { e ->
                    println("[$tag] Stored Google credentials look stale (${e.message}); clearing and retrying with a fresh sign-in...")
                    logout()
                }
            )

            println("[$tag] Google Login Successful!")
            Pair(accessToken, credential.refreshToken)
        } catch (e: Exception) {
            val errorMsg = "Login failed: ${e.message}"
            println("[$tag] ERROR: $errorMsg")
            if (e.message?.contains("8888") == true) {
                println("[$tag] HINT: Something else is using port 8888. Please close other apps or restart your computer.")
            }
            throw Exception(errorMsg)
        }
    }

    /** Runs the (browser-based, when needed) authorization flow and returns the resulting credential. */
    private fun authorize(): Credential {
        val flow = buildFlow()
        // Fixed port 8888 for consistent Redirect URI matching
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        println("[$tag] Attempting to open your default browser for sign-in...")
        // This is the call that triggers the system browser when no valid stored credential exists
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

    actual suspend fun refreshAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val flow = buildFlow()
            val credential = flow.loadCredential("user") ?: return@withContext null
            if (credential.refreshToken()) {
                credential.accessToken
            } else {
                null
            }
        } catch (e: Exception) {
            println("[$tag] Automatic token refresh failed: ${e.message}")
            null
        }
    }

    actual fun logout() {
        try {
            if (credentialsDir.exists()) {
                val success = credentialsDir.deleteRecursively()
                println("[$tag] Local session cleared: $success")
            }
        } catch (e: Exception) {
            println("[$tag] Logout error: ${e.message}")
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
            println("[$tag] Failed to load .env file: ${e.message}")
        }
        return envMap
    }

    internal fun buildFlow(): GoogleAuthorizationCodeFlow {
        // 1. Try environment variables (JVM properties or system env)
        var clientId = System.getProperty("GOOGLE_CLIENT_ID") ?: System.getenv("GOOGLE_CLIENT_ID")
        var clientSecret = System.getProperty("GOOGLE_CLIENT_SECRET") ?: System.getenv("GOOGLE_CLIENT_SECRET")

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
            val envPath = System.getProperty("CEF_GOOGLE_CLIENT_SECRET_PATH") ?: System.getenv("CEF_GOOGLE_CLIENT_SECRET_PATH")
            val defaultPath = Paths.get(System.getProperty("user.home"), ".cef", "client_secret.json").toString()
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
            transport, jsonFactory, clientSecrets, scopes
        ).setDataStoreFactory(FileDataStoreFactory(credentialsDir))
            .setAccessType("offline")
            .setApprovalPrompt("force") // Ensures a fresh login every time you click Connect
            .build()
    }
}
