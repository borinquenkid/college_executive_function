package com.borinquenterrier.cef

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {

    @Test
    fun `codeChallengeS256 matches RFC 7636 Appendix B worked example`() {
        // https://datatracker.ietf.org/doc/html/rfc7636#appendix-B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, Pkce.codeChallengeS256(verifier))
    }

    @Test
    fun `generateCodeVerifier produces a valid-length unreserved-charset string`() {
        val verifier = Pkce.generateCodeVerifier()
        assertTrue(verifier.length in 43..128, "verifier length ${verifier.length} outside RFC 7636 bounds")
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "verifier contains non-unreserved characters: $verifier")
    }

    @Test
    fun `generateCodeVerifier produces different values each call`() {
        val a = Pkce.generateCodeVerifier()
        val b = Pkce.generateCodeVerifier()
        assertNotEquals(a, b)
    }

    @Test
    fun `codeChallengeS256 is deterministic for the same verifier`() {
        val verifier = Pkce.generateCodeVerifier()
        assertEquals(Pkce.codeChallengeS256(verifier), Pkce.codeChallengeS256(verifier))
    }
}
