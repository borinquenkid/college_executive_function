package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf

class OtelTracerTest : FunSpec({

    val ENDPOINT_KEY = "CEF_OTLP_ENDPOINT"
    val USER_KEY     = "CEF_OTLP_USER"
    val PASS_KEY     = "CEF_OTLP_PASSWORD"

    fun clearProps() {
        System.clearProperty(ENDPOINT_KEY)
        System.clearProperty(USER_KEY)
        System.clearProperty(PASS_KEY)
    }

    afterEach { clearProps() }

    // ── OtelTracer.create() ───────────────────────────────────────────────────

    test("create() returns null when no env vars are set") {
        clearProps()
        OtelTracer.create(AppEnv(emptyMap())) shouldBe null
    }

    test("create() returns null when only endpoint is set") {
        System.setProperty(ENDPOINT_KEY, "http://localhost:4318")
        OtelTracer.create(AppEnv(emptyMap())) shouldBe null
    }

    test("create() returns null when only user and password are set") {
        System.setProperty(USER_KEY, "u")
        System.setProperty(PASS_KEY, "p")
        OtelTracer.create(AppEnv(emptyMap())) shouldBe null
    }

    test("create() returns OtelTracer when all three vars are set") {
        System.setProperty(ENDPOINT_KEY, "http://localhost:4318")
        System.setProperty(USER_KEY, "testuser")
        System.setProperty(PASS_KEY, "testpass")
        val tracer = OtelTracer.create(AppEnv(emptyMap()))
        tracer shouldNotBe null
        tracer!!.shutdown() // clean up SDK
    }

    // ── AppEnv ────────────────────────────────────────────────────────────────

    test("AppEnv.get returns null for unknown key") {
        AppEnv(emptyMap()).get("NONEXISTENT_KEY_XYZ_12345") shouldBe null
    }

    test("AppEnv.get reads JVM system property") {
        System.setProperty("CEF_TEST_PROP", "hello")
        try {
            AppEnv(emptyMap()).get("CEF_TEST_PROP") shouldBe "hello"
        } finally {
            System.clearProperty("CEF_TEST_PROP")
        }
    }

    test("AppEnv.get treats blank system property as null (falls through)") {
        System.setProperty("CEF_TEST_BLANK", "   ")
        try {
            // no env var set either, so result is null
            AppEnv(emptyMap()).get("CEF_TEST_BLANK") shouldBe null
        } finally {
            System.clearProperty("CEF_TEST_BLANK")
        }
    }

    // ── createTracer() factory ────────────────────────────────────────────────

    test("createTracer() returns NoopTracer when vars are missing") {
        clearProps()
        val settings = io.mockk.mockk<com.russhwolf.settings.Settings>(relaxed = true)
        val tracer = createTracer(settings, AppEnv(emptyMap()))
        tracer shouldBe NoopTracer
    }

    test("createTracer() returns OtelTracer when vars are present") {
        System.setProperty(ENDPOINT_KEY, "http://localhost:4318")
        System.setProperty(USER_KEY, "u")
        System.setProperty(PASS_KEY, "p")
        val settings = io.mockk.mockk<com.russhwolf.settings.Settings>(relaxed = true)
        val tracer = createTracer(settings, AppEnv(emptyMap()))
        tracer.shouldBeInstanceOf<OtelTracer>()
        tracer.shutdown()
    }

    // ── ReleaseTelemetryCheck (HARD-1) ─────────────────────────────────────────

    test("ReleaseTelemetryCheck.missingSecrets lists every unset key") {
        clearProps()
        ReleaseTelemetryCheck.missingSecrets(AppEnv(emptyMap())) shouldBe
            listOf("CEF_OTLP_ENDPOINT", "CEF_OTLP_USER", "CEF_OTLP_PASSWORD")
    }

    test("ReleaseTelemetryCheck.missingSecrets is empty when all three are set") {
        System.setProperty(ENDPOINT_KEY, "http://localhost:4318")
        System.setProperty(USER_KEY, "u")
        System.setProperty(PASS_KEY, "p")
        ReleaseTelemetryCheck.missingSecrets(AppEnv(emptyMap())) shouldBe emptyList()
    }

    test("ReleaseTelemetryCheck.failureMessage is null on a non-release build even when secrets are missing") {
        clearProps()
        ReleaseTelemetryCheck.failureMessage(AppEnv(emptyMap()), isReleaseBuild = false) shouldBe null
    }

    test("ReleaseTelemetryCheck.failureMessage is null on a release build when secrets are present") {
        System.setProperty(ENDPOINT_KEY, "http://localhost:4318")
        System.setProperty(USER_KEY, "u")
        System.setProperty(PASS_KEY, "p")
        ReleaseTelemetryCheck.failureMessage(AppEnv(emptyMap()), isReleaseBuild = true) shouldBe null
    }

    test("ReleaseTelemetryCheck.failureMessage names the missing secrets on a release build") {
        clearProps()
        System.setProperty(ENDPOINT_KEY, "http://localhost:4318")
        val message = ReleaseTelemetryCheck.failureMessage(AppEnv(emptyMap()), isReleaseBuild = true)
        message shouldNotBe null
        message!! shouldContain "CEF_OTLP_USER"
        message shouldContain "CEF_OTLP_PASSWORD"
        message shouldNotContain "CEF_OTLP_ENDPOINT"
    }
})
