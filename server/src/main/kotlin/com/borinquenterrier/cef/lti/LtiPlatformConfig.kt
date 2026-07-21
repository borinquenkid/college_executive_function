package com.borinquenterrier.cef.lti

/**
 * One self-hosted deployment serves one institution, normally one LMS instance — so this is a
 * single registered platform, not a multi-tenant registry of them. If an institution ever needs
 * to register more than one LMS instance against the same deployment, that's a small, additive
 * follow-up (a list instead of one config), not a redesign.
 */
data class LtiPlatformConfig(
    val issuer: String,
    val clientId: String,
    val deploymentIds: Set<String>,
    val authLoginUrl: String,
    val jwksUrl: String
) {
    companion object {
        /**
         * LTI is the only login path (see docs/adr/0006-lti-1.3-only-auth.md) — an unconfigured
         * deployment has no way for anyone to ever log in, so fail fast at boot with a message
         * pointing at the setup doc rather than silently starting into a dead-end state.
         */
        fun resolveFromEnv(): LtiPlatformConfig {
            fun required(name: String): String =
                System.getenv(name)?.takeIf { it.isNotBlank() }
                    ?: error(
                        "$name is required — LTI is the only login path for this deployment. " +
                            "See DEPLOYMENT.md's \"Registering CEF as an LTI tool\" section."
                    )

            return LtiPlatformConfig(
                issuer = required("CEF_LTI_ISSUER"),
                clientId = required("CEF_LTI_CLIENT_ID"),
                deploymentIds = required("CEF_LTI_DEPLOYMENT_IDS")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
                authLoginUrl = required("CEF_LTI_AUTH_LOGIN_URL"),
                jwksUrl = required("CEF_LTI_JWKS_URL")
            )
        }
    }
}
