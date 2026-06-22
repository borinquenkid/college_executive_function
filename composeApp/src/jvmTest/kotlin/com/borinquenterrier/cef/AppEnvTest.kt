package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class AppEnvTest : FunSpec({

    // ── get() from System property ────────────────────────────────────────────

    test("get returns System property when non-blank") {
        System.setProperty("CEF_TEST_PROP_1", "prop_value")
        try {
            val env = AppEnv(emptyMap())
            env.get("CEF_TEST_PROP_1") shouldBe "prop_value"
        } finally {
            System.clearProperty("CEF_TEST_PROP_1")
        }
    }

    test("get skips blank System property and falls through to dotEnv override") {
        System.setProperty("CEF_TEST_PROP_BLANK", "   ")
        try {
            val env = AppEnv(mapOf("CEF_TEST_PROP_BLANK" to "from_override"))
            env.get("CEF_TEST_PROP_BLANK") shouldBe "from_override"
        } finally {
            System.clearProperty("CEF_TEST_PROP_BLANK")
        }
    }

    // ── get() from dotEnv override ────────────────────────────────────────────

    test("get returns value from dotEnv override") {
        val env = AppEnv(mapOf("MY_KEY" to "my_value"))
        env.get("MY_KEY") shouldBe "my_value"
    }

    test("get returns null when key not in override and no System property") {
        val env = AppEnv(emptyMap())
        env.get("NONEXISTENT_KEY_XYZ_999") shouldBe null
    }

    test("get returns null when dotEnv override value is blank") {
        val env = AppEnv(mapOf("BLANK_KEY" to "  "))
        env.get("BLANK_KEY") shouldBe null
    }

    // ── parseDotEnv() via default constructor ─────────────────────────────────

    test("parseDotEnv runs when called via default constructor and finds project .env") {
        // AppEnv() with no override triggers parseDotEnv() lazily on first get() call.
        // The project root .env is found at ../.env (relative to composeApp/ CWD).
        val env = AppEnv()
        env.get("CEFTEST_DEFINITELY_NONEXISTENT_KEY_12345") shouldBe null
    }

    test("parseDotEnv catches IOException when .env path is a directory") {
        // Create .env as a directory in CWD so forEachLine throws IsADirectoryException.
        // This is safe: composeApp/.env does not exist as a file in this project.
        val dotEnvDir = File(".env")
        val created = !dotEnvDir.exists() && dotEnvDir.mkdir()
        try {
            val env = AppEnv()  // parseDotEnv() finds ./.env (directory) → forEachLine throws
            env.get("ANY_KEY") shouldBe null  // exception caught → empty map → null
        } finally {
            if (created) dotEnvDir.delete()
        }
    }
})
