package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class GoogleAuthService actual constructor(private val settings: Settings, private val appEnv: AppEnv) {

    private val clientId =
        "1014783111965-ttk2sf44ue2k97qjucvemqgmr15cjv3n.apps.googleusercontent.com"
    private val redirectUri =
        "com.googleusercontent.apps.1014783111965-ttk2sf44ue2k97qjucvemqgmr15cjv3n:/"
    private val scope =
        "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/drive.readonly"

    private val httpClient = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }
    private val oauthExchange = OAuthExchange(httpClient)
    private val tokenRepository = GoogleTokenRepository(settings)

    // Strong reference to prevent GC while the session is active
    private var activeSession: ASWebAuthenticationSession? = null
    private val presentationProvider = PresentationContextProvider()

    actual suspend fun login(): Pair<String, String?> =
        suspendCancellableCoroutine { continuation ->
            val codeVerifier = Pkce.generateCodeVerifier()
            val codeChallenge = Pkce.codeChallengeS256(codeVerifier)

            val authUrlString = "https://accounts.google.com/o/oauth2/v2/auth?" +
                    "client_id=$clientId&" +
                    "redirect_uri=$redirectUri&" +
                    "response_type=code&" +
                    "scope=${scope.replace(" ", "%20")}&" +
                    "access_type=offline&" +
                    "prompt=consent&" +
                    "code_challenge=$codeChallenge&" +
                    "code_challenge_method=S256"

            val authUrl = NSURL(string = authUrlString)
            if (authUrl == null) {
                continuation.resumeWithException(Exception("Failed to build Google auth URL"))
                return@suspendCancellableCoroutine
            }

            val session = ASWebAuthenticationSession(
                uRL = authUrl,
                callbackURLScheme = "com.googleusercontent.apps.1014783111965-ttk2sf44ue2k97qjucvemqgmr15cjv3n",
                completionHandler = { callbackUrl: NSURL?, error: NSError? ->
                    activeSession = null // Release reference

                    val callbackString = callbackUrl?.absoluteString()
                    if (callbackString != null) {
                        val code = extractCode(callbackString)
                        if (code != null) {
                            GlobalScope.launch {
                                try {
                                    val tokenResponse = oauthExchange.exchangeCodeForTokens(
                                        code = code,
                                        clientId = clientId,
                                        clientSecret = null,
                                        redirectUri = redirectUri,
                                        codeVerifier = codeVerifier
                                    )
                                    tokenRepository.saveTokens(
                                        tokenResponse.access_token,
                                        tokenResponse.refresh_token
                                    )
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            Pair(
                                                tokenResponse.access_token,
                                                tokenResponse.refresh_token
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    if (continuation.isActive) continuation.resumeWithException(e)
                                }
                            }
                        } else {
                            if (continuation.isActive) {
                                continuation.resumeWithException(Exception("No code found in callback URL"))
                            }
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                Exception(
                                    error?.localizedDescription ?: "User cancelled"
                                )
                            )
                        }
                    }
                }
            )

            session.presentationContextProvider = presentationProvider
            activeSession = session
            // Ties the native session's lifetime to the coroutine: if login() is cancelled
            // (e.g. the caller's scope is torn down while the auth sheet is still up), the
            // session is dismissed instead of completing later and trying to resume an
            // already-cancelled continuation (which would throw IllegalStateException).
            continuation.invokeOnCancellation {
                session.cancel()
                activeSession = null
            }
            session.start()
        }

    private fun extractCode(url: String): String? {
        val components = NSURLComponents(string = url)
        return components.queryItems?.filterIsInstance<NSURLQueryItem>()
            ?.firstOrNull { it.name == "code" }?.value
    }

    actual suspend fun refreshAccessToken(refreshToken: String): String? {
        return try {
            val response = oauthExchange.refreshAccessToken(
                refreshToken = refreshToken,
                clientId = clientId,
                clientSecret = null
            )
            tokenRepository.saveTokens(response.access_token, refreshToken)
            response.access_token
        } catch (e: Exception) {
            null
        }
    }

    actual fun logout() {
        tokenRepository.clearTokens()
    }
}

private class PresentationContextProvider : NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        return findKeyWindow() ?: ASPresentationAnchor()
    }
}
