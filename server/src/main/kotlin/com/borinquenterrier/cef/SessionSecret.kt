package com.borinquenterrier.cef

import java.io.File
import java.security.SecureRandom
import java.util.Base64

/** Resolves the HMAC key used to sign session cookies (see Application.kt's Sessions install).
 *  Prefers CEF_SESSION_SECRET so an operator can pin it explicitly across a fleet; otherwise
 *  generates one on first boot and persists it alongside the tenant data so a container restart
 *  doesn't invalidate every already-logged-in student's session. */
object SessionSecret {
    fun resolve(tenantBaseDir: String): ByteArray {
        System.getenv("CEF_SESSION_SECRET")?.takeIf { it.isNotBlank() }?.let {
            return it.toByteArray(Charsets.UTF_8)
        }
        val secretFile = File(tenantBaseDir, ".session-secret")
        if (secretFile.exists()) {
            return Base64.getDecoder().decode(secretFile.readText().trim())
        }
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        secretFile.parentFile?.mkdirs()
        secretFile.writeText(Base64.getEncoder().encodeToString(generated))
        return generated
    }
}
