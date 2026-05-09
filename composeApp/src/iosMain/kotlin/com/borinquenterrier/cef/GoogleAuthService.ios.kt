package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlinx.coroutines.suspendCancellableCoroutine
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class GoogleAuthService actual constructor(private val settings: Settings) {

    private val clientId = "118849293337-tiambsi7u4hqq03rnaj0tohppqqu8fsa.apps.googleusercontent.com"
    private val redirectUri = "com.googleusercontent.apps.118849293337-tiambsi7u4hqq03rnaj0tohppqqu8fsa:/"
    private val scope = "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/drive.readonly"
    
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

    actual suspend fun login(): Pair<String, String?> = suspendCancellableCoroutine { continuation ->
        val authUrlString = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=code&" +
                "scope=${scope.replace(" ", "%20")}&" +
                "access_type=offline&" +
                "prompt=consent"

        val session = ASWebAuthenticationSession(
            uRL = NSURL(string = authUrlString)!!,
            callbackURLScheme = "com.googleusercontent.apps.118849293337-tiambsi7u4hqq03rnaj0tohppqqu8fsa",
            completionHandler = { callbackUrl: NSURL?, error: NSError? ->
                activeSession = null // Release reference
                
                if (callbackUrl != null) {
                    val code = extractCode(callbackUrl.absoluteString()!!)
                    if (code != null) {
                        GlobalScope.launch {
                            try {
                                val tokenResponse = oauthExchange.exchangeCodeForTokens(
                                    code = code,
                                    clientId = clientId,
                                    clientSecret = null,
                                    redirectUri = redirectUri
                                )
                                tokenRepository.saveTokens(tokenResponse.access_token, tokenResponse.refresh_token)
                                continuation.resume(Pair(tokenResponse.access_token, tokenResponse.refresh_token))
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }
                    } else {
                        continuation.resumeWithException(Exception("No code found in callback URL"))
                    }
                } else {
                    continuation.resumeWithException(Exception(error?.localizedDescription ?: "User cancelled"))
                }
            }
        )
        
        session.presentationContextProvider = presentationProvider
        activeSession = session
        session.start()
    }

    private fun extractCode(url: String): String? {
        val components = NSURLComponents(string = url)
        return components.queryItems?.filterIsInstance<NSURLQueryItem>()?.firstOrNull { it.name == "code" }?.value
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

private class PresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        return UIApplication.sharedApplication.keyWindow ?: ASPresentationAnchor()
    }
}
