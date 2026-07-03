package com.borinquenterrier.cef

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.random.Random

/**
 * RFC 7636 PKCE (Proof Key for Code Exchange) helpers for the iOS OAuth flow, which — unlike
 * Android's `GoogleSignInClient`-based flow — is a browser-redirect exchange with no client
 * secret. PKCE binds the authorization code to the client that requested it, so an intercepted
 * redirect can't be replayed by anything that doesn't also have the original code_verifier.
 *
 * Pure Kotlin/common (no platform APIs), so it's testable via jvmTest without needing iOS test
 * infrastructure to exist first.
 */
object Pkce {
    /** A random 43-character (32 raw bytes, base64url-encoded, unpadded) code verifier. */
    fun generateCodeVerifier(): String =
        Random.nextBytes(32).toByteString().base64Url().trimEnd('=')

    /** The S256 code challenge for a given verifier, per RFC 7636 §4.2. */
    fun codeChallengeS256(verifier: String): String =
        verifier.encodeUtf8().sha256().base64Url().trimEnd('=')
}
