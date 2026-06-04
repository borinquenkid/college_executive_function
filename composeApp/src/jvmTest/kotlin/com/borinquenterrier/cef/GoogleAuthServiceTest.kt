package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
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
})
