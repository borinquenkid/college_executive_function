package com.borinquenterrier.cef.lti

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.borinquenterrier.cef.OAuthStateStore
import com.borinquenterrier.cef.randomHexId
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

const val LEARNER_ROLE = "http://purl.imsglobal.org/vocab/lis/v2/membership#Learner"
const val INSTRUCTOR_ROLE = "http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"

/**
 * Everything the test suite needs to drive a real POST /lti/launch without a real LMS: one shared
 * RSA keypair, a fake JwkProvider wrapping its public half (so LtiLaunchVerifier never makes a
 * live HTTPS call), and a JWT signer using the private half. Replaces the old
 * /api/auth/start-based test setup — see docs/adr/0006-lti-1.3-only-auth.md.
 */
object LtiTestSupport {
    private const val KEY_ID = "test-key-1"
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    val config = LtiPlatformConfig(
        issuer = "https://test-lms.example.edu",
        clientId = "test-client-id",
        deploymentIds = setOf("test-deployment"),
        authLoginUrl = "https://test-lms.example.edu/auth",
        // Never actually fetched over the network — LtiLaunchVerifier is constructed with
        // `jwkProvider` below instead of letting it build a real JwkProviderBuilder from this URL.
        jwksUrl = "https://test-lms.example.edu/jwks"
    )

    val jwkProvider: JwkProvider = JwkProvider { _ ->
        Jwk.fromValues(
            mapOf(
                "kid" to KEY_ID,
                "kty" to "RSA",
                "alg" to "RS256",
                "use" to "sig",
                "n" to publicKey.modulus.toBase64Url(),
                "e" to publicKey.publicExponent.toBase64Url()
            )
        )
    }

    fun verifier(config: LtiPlatformConfig = this.config): LtiLaunchVerifier = LtiLaunchVerifier(config, jwkProvider)

    fun signLaunchJwt(
        config: LtiPlatformConfig = this.config,
        subject: String = "test-student",
        nonce: String,
        deploymentId: String = config.deploymentIds.first(),
        roles: List<String> = listOf(LEARNER_ROLE),
        messageType: String = "LtiResourceLinkRequest",
        version: String = "1.3.0",
        expiresInMillis: Long = 60_000
    ): String {
        val algorithm = Algorithm.RSA256(publicKey, privateKey)
        return JWT.create()
            .withKeyId(KEY_ID)
            .withIssuer(config.issuer)
            .withAudience(config.clientId)
            .withSubject(subject)
            .withClaim("nonce", nonce)
            .withClaim("https://purl.imsglobal.org/spec/lti/claim/message_type", messageType)
            .withClaim("https://purl.imsglobal.org/spec/lti/claim/version", version)
            .withClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id", deploymentId)
            .withClaim("https://purl.imsglobal.org/spec/lti/claim/roles", roles)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + expiresInMillis))
            .sign(algorithm)
    }

    /** JWK's "n"/"e" fields are unsigned big-endian base64url — BigInteger.toByteArray() can
     *  prepend a 0x00 sign byte that must be stripped or the reconstructed key is wrong. */
    private fun BigInteger.toBase64Url(): String {
        var bytes = toByteArray()
        if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes = bytes.copyOfRange(1, bytes.size)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/**
 * Drives a real `POST /lti/launch` — bypassing `/lti/login`'s redirect chain, which is covered by
 * its own dedicated test — to establish a session the way production actually does: mint a
 * state+nonce, sign a launch JWT, and let the real handler verify it end-to-end.
 */
suspend fun HttpClient.loginViaLti(
    config: LtiPlatformConfig = LtiTestSupport.config,
    subject: String = "test-student",
    roles: List<String> = listOf(LEARNER_ROLE),
    deploymentId: String = config.deploymentIds.first()
): HttpResponse {
    val nonce = randomHexId(32)
    val state = OAuthStateStore.create(nonce)
    val idToken = LtiTestSupport.signLaunchJwt(
        config = config, subject = subject, nonce = nonce, deploymentId = deploymentId, roles = roles
    )
    return post("/lti/launch") {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("id_token", idToken)
                    append("state", state)
                }
            )
        )
    }
}
