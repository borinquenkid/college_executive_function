package com.borinquenterrier.cef.lti

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.borinquenterrier.cef.ContentHasher
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

private const val CLAIM_MESSAGE_TYPE = "https://purl.imsglobal.org/spec/lti/claim/message_type"
private const val CLAIM_VERSION = "https://purl.imsglobal.org/spec/lti/claim/version"
private const val CLAIM_DEPLOYMENT_ID = "https://purl.imsglobal.org/spec/lti/claim/deployment_id"
private const val CLAIM_ROLES = "https://purl.imsglobal.org/spec/lti/claim/roles"

data class LtiLaunchClaims(
    /** "lti-" + sha256("iss|deployment_id|sub") — a stable identity derived from three LTI
     *  claims, chosen so the existing hash-partitioned per-tenant storage (TenantDatabaseFactory)
     *  needs no changes: it just needs *a* stable string, same as the old random studentId. */
    val studentId: String,
    val roleUris: List<String>
)

class LtiLaunchVerificationException(message: String) : Exception(message)

/**
 * Verifies an LTI 1.3 Resource Link launch id_token against the platform's JWKS: signature,
 * issuer, audience (+ azp when audience has multiple entries, per OIDC core 3.1.3.7), nonce,
 * message_type/version, and that deployment_id is one we actually registered. Every failure
 * throws [LtiLaunchVerificationException] — callers should treat all of them the same way (401);
 * the message is for logs, not the client, since a specific reason is a hint for an attacker.
 */
class LtiLaunchVerifier(
    private val config: LtiPlatformConfig,
    // Overridable so tests can supply a fake provider backed by a locally-generated keypair
    // instead of making a real HTTPS call to a platform's JWKS endpoint.
    private val jwkProvider: JwkProvider = JwkProviderBuilder(URI(config.jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
) {

    fun verify(idToken: String, expectedNonce: String): LtiLaunchClaims {
        val unverified = try {
            JWT.decode(idToken)
        } catch (e: Exception) {
            throw LtiLaunchVerificationException("malformed id_token: ${e.message}")
        }

        val keyId = unverified.keyId
            ?: throw LtiLaunchVerificationException("id_token header missing kid")
        val jwk = try {
            jwkProvider.get(keyId)
        } catch (e: Exception) {
            throw LtiLaunchVerificationException("no matching JWKS key for kid=$keyId: ${e.message}")
        }
        val algorithm = Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null)

        val verified = try {
            JWT.require(algorithm)
                .withIssuer(config.issuer)
                .withAudience(config.clientId)
                .build()
                .verify(idToken)
        } catch (e: JWTVerificationException) {
            throw LtiLaunchVerificationException("signature/claim verification failed: ${e.message}")
        }

        if (verified.audience.size > 1 && verified.getClaim("azp").asString() != config.clientId) {
            throw LtiLaunchVerificationException("azp mismatch for multi-audience token")
        }
        if (verified.getClaim("nonce").asString() != expectedNonce) {
            throw LtiLaunchVerificationException("nonce mismatch")
        }
        if (verified.getClaim(CLAIM_MESSAGE_TYPE).asString() != "LtiResourceLinkRequest") {
            throw LtiLaunchVerificationException("unsupported message_type")
        }
        if (verified.getClaim(CLAIM_VERSION).asString() != "1.3.0") {
            throw LtiLaunchVerificationException("unsupported LTI version")
        }
        val deploymentId = verified.getClaim(CLAIM_DEPLOYMENT_ID).asString()
            ?: throw LtiLaunchVerificationException("missing deployment_id")
        if (deploymentId !in config.deploymentIds) {
            throw LtiLaunchVerificationException("unregistered deployment_id: $deploymentId")
        }
        val subject = verified.subject
            ?: throw LtiLaunchVerificationException("missing sub")
        val roles = verified.getClaim(CLAIM_ROLES).asList(String::class.java) ?: emptyList()

        val studentId = "lti-" + ContentHasher.hashString("${config.issuer}|$deploymentId|$subject")
        return LtiLaunchClaims(studentId = studentId, roleUris = roles)
    }
}
