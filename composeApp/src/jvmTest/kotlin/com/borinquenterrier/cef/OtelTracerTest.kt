package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
})
