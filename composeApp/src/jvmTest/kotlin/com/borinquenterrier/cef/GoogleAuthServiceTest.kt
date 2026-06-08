package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import com.russhwolf.settings.MapSettings
import io.kotest.assertions.throwables.shouldThrow
import java.io.File
import java.nio.file.Files

class GoogleAuthServiceTest : FunSpec({

    lateinit var tempCredentialsDir: File

    beforeEach {
        // Clean up system properties
        System.clearProperty("GOOGLE_CLIENT_ID")
        System.clearProperty("GOOGLE_CLIENT_SECRET")
        System.clearProperty("CEF_GOOGLE_CLIENT_SECRET_PATH")
        System.setProperty("CEF_BYPASS_BUILD_SECRETS", "true")
        
        // Setup isolated temp credentials dir
        tempCredentialsDir = Files.createTempDirectory("cef-test-credentials").toFile()
        System.setProperty("CEF_CREDENTIALS_DIR", tempCredentialsDir.absolutePath)
    }

    afterEach {
        // Clean up system properties and temp dir
        System.clearProperty("GOOGLE_CLIENT_ID")
        System.clearProperty("GOOGLE_CLIENT_SECRET")
        System.clearProperty("CEF_GOOGLE_CLIENT_SECRET_PATH")
        System.clearProperty("CEF_CREDENTIALS_DIR")
        System.clearProperty("CEF_BYPASS_BUILD_SECRETS")
        tempCredentialsDir.deleteRecursively()
    }

    test("should use build-in BuildSecrets if available and not bypassed") {
        if (BuildSecrets.GOOGLE_CLIENT_ID != null && BuildSecrets.GOOGLE_CLIENT_SECRET != null) {
            System.clearProperty("GOOGLE_CLIENT_ID")
            System.clearProperty("GOOGLE_CLIENT_SECRET")
            System.setProperty("CEF_BYPASS_BUILD_SECRETS", "false")
            
            // Backup the real .env if it exists so loadEnvFile() doesn't find it
            val envFile = File(".env")
            val backupFile = File(".env.bak")
            var backedUp = false
            if (envFile.exists()) {
                envFile.renameTo(backupFile)
                backedUp = true
            }
            try {
                val authService = GoogleAuthService(MapSettings())
                val flow = authService.buildFlow()
                flow shouldNotBe null
                flow.clientId shouldBe BuildSecrets.GOOGLE_CLIENT_ID
            } finally {
                if (backedUp) {
                    backupFile.renameTo(envFile)
                }
            }
        }
    }

    test("should fail if no environment variables, .env file, or client_secret.json exist") {
        // Backup the real .env if it exists
        val envFile = File(".env")
        val backupFile = File(".env.bak")
        var backedUp = false
        if (envFile.exists()) {
            envFile.renameTo(backupFile)
            backedUp = true
        }

        try {
            // Set CEF_GOOGLE_CLIENT_SECRET_PATH to a non-existent file path to force failure
            System.setProperty("CEF_GOOGLE_CLIENT_SECRET_PATH", "non_existent_file.json")
            
            val authService = GoogleAuthService(MapSettings())
            
            val exception = shouldThrow<IllegalStateException> {
                authService.buildFlow()
            }
            
            exception.message shouldBe "Google Client ID/Secret not found in environment variables or .env file, and client_secret.json not found at non_existent_file.json. Please configure it."
        } finally {
            // Restore .env
            if (backedUp) {
                backupFile.renameTo(envFile)
            }
        }
    }

    test("should successfully construct buildFlow when GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET system properties are set") {
        System.setProperty("GOOGLE_CLIENT_ID", "dummy-id.apps.googleusercontent.com")
        System.setProperty("GOOGLE_CLIENT_SECRET", "dummy-secret")

        val authService = GoogleAuthService(MapSettings())

        val flow = authService.buildFlow()
        flow shouldNotBe null
        flow.clientId shouldBe "dummy-id.apps.googleusercontent.com"
    }

    // --- resolveAccessToken: closes the "Connect Google Account" crash where a credential
    // restored from a prior session has accessToken == null (only its refreshToken was
    // persisted), and that null was handed straight to GoogleTokenRepository.saveTokens,
    // whose `accessToken` parameter is non-null — crashing with
    // "Parameter specified as non-null is null: ... saveTokens, parameter accessToken".

    test("resolveAccessToken returns the current access token without refreshing when one is already present") {
        val authService = GoogleAuthService(MapSettings())
        var refreshCalls = 0

        val token = authService.resolveAccessToken("existing-token") {
            refreshCalls++
            "should-not-be-used"
        }

        token shouldBe "existing-token"
        refreshCalls shouldBe 0
    }

    test("resolveAccessToken refreshes and returns the new token when the current one is null — the StoredCredential restore case") {
        val authService = GoogleAuthService(MapSettings())

        val token = authService.resolveAccessToken(null) { "refreshed-token" }

        token shouldBe "refreshed-token"
    }

    test("resolveAccessToken throws a clear, actionable error when refreshing also yields no token, instead of crashing deep inside saveTokens") {
        val authService = GoogleAuthService(MapSettings())

        val exception = shouldThrow<Exception> {
            authService.resolveAccessToken(null) { null }
        }

        exception.message shouldBe "Google sign-in succeeded but no access token could be obtained. Please disconnect and reconnect your Google account."
    }

    // --- signInRetryingOnStaleCredential: closes the "401 Unauthorized POST
    // https://oauth2.googleapis.com/token" failure loop where a stored credential's
    // refresh token had been revoked — the old code surfaced the raw 401 and never
    // recovered. The fix detects the failure, clears the stale local session, and
    // retries once with a brand-new interactive sign-in.

    test("signInRetryingOnStaleCredential returns the first credential and token without touching the stale-credential callback when the first attempt succeeds") {
        val authService = GoogleAuthService(MapSettings())
        var authorizeCalls = 0
        var staleCallbackCalls = 0

        val (credential, token) = authService.signInRetryingOnStaleCredential(
            authorize = { authorizeCalls++; "credential-$authorizeCalls" },
            obtainAccessToken = { c -> "token-for-$c" },
            onStaleCredential = { staleCallbackCalls++ }
        )

        credential shouldBe "credential-1"
        token shouldBe "token-for-credential-1"
        authorizeCalls shouldBe 1
        staleCallbackCalls shouldBe 0
    }

    test("signInRetryingOnStaleCredential clears the stale session and retries with a fresh sign-in when the first attempt fails — the 401 Unauthorized recovery path") {
        val authService = GoogleAuthService(MapSettings())
        var authorizeCalls = 0
        val staleCallbackExceptions = mutableListOf<Exception>()

        val (credential, token) = authService.signInRetryingOnStaleCredential(
            authorize = { authorizeCalls++; "credential-$authorizeCalls" },
            obtainAccessToken = { c ->
                if (c == "credential-1") throw Exception("401 Unauthorized\nPOST https://oauth2.googleapis.com/token")
                "token-for-$c"
            },
            onStaleCredential = { e -> staleCallbackExceptions.add(e) }
        )

        credential shouldBe "credential-2"
        token shouldBe "token-for-credential-2"
        authorizeCalls shouldBe 2
        staleCallbackExceptions shouldHaveSize 1
        staleCallbackExceptions[0].message shouldBe "401 Unauthorized\nPOST https://oauth2.googleapis.com/token"
    }

    test("signInRetryingOnStaleCredential propagates the failure when even the fresh sign-in cannot produce a token") {
        val authService = GoogleAuthService(MapSettings())
        var authorizeCalls = 0

        val exception = shouldThrow<Exception> {
            authService.signInRetryingOnStaleCredential(
                authorize = { authorizeCalls++; "credential-$authorizeCalls" },
                obtainAccessToken = { throw Exception("still failing") },
                onStaleCredential = { }
            )
        }

        exception.message shouldBe "still failing"
        authorizeCalls shouldBe 2
    }
})
