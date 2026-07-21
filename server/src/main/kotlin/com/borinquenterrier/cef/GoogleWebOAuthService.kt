package com.borinquenterrier.cef

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import java.io.StringReader

/**
 * Config for the server's *own* Google OAuth client — deliberately separate from the
 * GOOGLE_CLIENT_ID/SECRET the desktop/Android apps use. Google's Desktop-app OAuth client type is
 * restricted to loopback/OOB redirect URIs and cannot be reused for a browser redirect flow; this
 * needs a second, Web-application-type client with an authorized redirect URI pointed at
 * CEF_APP_BASE_URL/api/auth/google/callback. See README.md's Google Cloud Console section and
 * docs/adr/0008-self-serve-google-oauth-web-flow.md.
 *
 * Unlike LtiPlatformConfig, this is genuinely optional — Calendar sync is a nice-to-have, not a
 * login mechanism, so an unconfigured deployment just serves 503 on these two routes instead of
 * refusing to boot.
 */
data class GoogleWebOAuthConfig(val clientId: String, val clientSecret: String) {
    companion object {
        fun resolveFromEnv(): GoogleWebOAuthConfig? {
            val clientId = System.getenv("CEF_GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() } ?: return null
            val clientSecret = System.getenv("CEF_GOOGLE_WEB_CLIENT_SECRET")?.takeIf { it.isNotBlank() } ?: return null
            return GoogleWebOAuthConfig(clientId, clientSecret)
        }
    }
}

/**
 * Drives the authorization-code flow over HTTP redirects instead of GoogleAuthService.jvm.kt's
 * AuthorizationCodeInstalledApp/LocalServerReceiver (which opens a local browser + loopback
 * server — meaningless in a server context). MemoryDataStoreFactory is used purely because the
 * google-oauth-client library requires *some* credential store to construct a flow; nothing is
 * actually persisted there — tokens are written straight to the tenant's own
 * GoogleTokenRepository by the caller, same as the desktop/Android flows already do.
 */
class GoogleWebOAuthService(
    private val config: GoogleWebOAuthConfig,
    private val redirectUri: String,
    // Overridable so tests can avoid a real HTTPS call to Google's token endpoint. Left null
    // (rather than defaulting to a lambda calling buildFlow()) because a constructor parameter's
    // default value can't reference an instance method before `this` is fully initialized.
    private val tokenExchanger: ((code: String) -> GoogleTokenResponse)? = null
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val scopes = listOf("https://www.googleapis.com/auth/calendar")

    private fun buildFlow(): GoogleAuthorizationCodeFlow {
        val jsonString = """
            {
              "web": {
                "client_id": "${config.clientId}",
                "client_secret": "${config.clientSecret}",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token"
              }
            }
        """.trimIndent()
        val clientSecrets = GoogleClientSecrets.load(jsonFactory, StringReader(jsonString))
        return GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientSecrets, scopes)
            .setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
            .setAccessType("offline")
            .build()
    }

    /** prompt=consent forces Google to return a refresh token even if this student previously
     *  granted (and later revoked, or Google otherwise didn't re-issue) consent — access_type=
     *  offline alone only guarantees a refresh token on a student's *first* authorization. */
    fun authorizationUrl(state: String): String =
        buildFlow().newAuthorizationUrl()
            .setRedirectUri(redirectUri)
            .setState(state)
            .set("prompt", "consent")
            .build()

    fun exchangeCode(code: String): GoogleTokenResponse =
        tokenExchanger?.invoke(code)
            ?: buildFlow().newTokenRequest(code).setRedirectUri(redirectUri).execute()
}
