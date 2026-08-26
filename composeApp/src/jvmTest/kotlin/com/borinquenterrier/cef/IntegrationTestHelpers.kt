package com.borinquenterrier.cef

import io.github.cdimascio.dotenv.dotenv
import io.kotest.core.test.TestScope

/**
 * Shared helpers for AI integration tests that use a real Gemini API key.
 *
 * These tests are inherently I/O-bound and depend on an external service.
 * They skip gracefully when:
 *  - No API key is present in the environment
 *  - The daily quota (RPD) is exhausted — quota errors are NOT retried
 *
 * ## Timeout policy
 * Each AI integration test should set a `timeout` in `.config()` so that even
 * transient RPM throttling cannot cause a test to run for more than 3 minutes.
 */

/** Maximum wall-clock time for a single AI integration test (3 minutes). */
const val AI_INTEGRATION_TIMEOUT_MS = 3 * 60 * 1000L

/**
 * Credentials required by [LiveApiIntegrationTest].
 * All three fields must be non-null; [resolveLiveCredentials] fails fast otherwise.
 */
data class LiveCredentials(
    val refreshToken: String,
)

/**
 * Resolves a secret by checking, in order:
 *  1. OS environment variable (set by GitHub Actions via `env:` in the workflow)
 *  2. Local `.env` file (developer machine)
 *
 * Returns null if the key is absent or blank in both sources.
 */
private val dotenvInstance by lazy {
    dotenv {
        directory = listOf("../", "./").firstOrNull { java.io.File(it, ".env").exists() } ?: "./"
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }
}

private fun resolveSecret(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: runCatching { dotenvInstance[key] }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * Resolves all three live credentials required by [LiveApiIntegrationTest].
 * Fails immediately with a clear message if any credential is missing so that
 * CI surfaces a configuration problem rather than a cryptic test failure.
 */
fun resolveLiveCredentials(): LiveCredentials {
    fun require(vararg keys: String): String {
        for (key in keys) resolveSecret(key)?.let { return it }
        error("Required secret not found in env vars or .env: ${keys.joinToString(" / ")}")
    }
    return LiveCredentials(
        refreshToken = require("GOOGLE_REFRESH_TOKEN"),
    )
}

fun isTestProfile(): Boolean {
    val runProfile = System.getenv("RUN_PROFILE") ?: System.getProperty("runProfile") ?: ""
    val offline = System.getenv("OFFLINE") ?: System.getProperty("offline") ?: ""
    return runProfile.equals("test", ignoreCase = true) || offline.equals("true", ignoreCase = true)
}

/**
 * Points [AppTracer.current] at a real [OtelTracer] (service name "cef-eval-ci") the first
 * time a real API key is resolved, so GeminiRequestExecutor's spans/events — previously
 * silent no-ops via [NoopTracer] in every integration test run — actually reach OpenObserve.
 * Idempotent: only the first caller in the JVM pays the SDK-init cost; later calls no-op.
 * Safe when OTLP secrets aren't set (e.g. local dev) — [OtelTracer.create] falls back to null.
 */
private val tracerInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

private fun ensureTracerInitialized() {
    // Only arm live tracing in a -PrunAITests=true run (build.gradle.kts forwards the flag as
    // this system property). Without the guard, any test outside the integration exclusion
    // filters that touches resolveApiKey arms the real cef-eval-ci exporter for the whole
    // unit-test JVM, and every later test's MOCKED gemini spans flood production OpenObserve —
    // exactly the fake "model cascade" alert of 2026-08-25/26 (7.7k mock spans in one run).
    if (System.getProperty("runAITests") != "true") return
    if (tracerInitialized.compareAndSet(false, true)) {
        AppTracer.current = OtelTracer.create(AppEnv(), serviceName = "cef-eval-ci") ?: NoopTracer
    }
}

/**
 * Reads the Gemini API key from OS env vars or the local `.env` file.
 * Returns null (and prints a skip message) if no key is found.
 */
fun resolveApiKey(testName: String): String? {
    if (isTestProfile()) {
        println("SKIPPING $testName: Disabled in test/offline profile.")
        return null
    }
    val apiKey = resolveSecret("CEF_GEMINI_API_KEY") ?: resolveSecret("GEMINI_API_KEY")
    if (apiKey == null) {
        println("SKIPPING $testName: No Gemini API Key found in env or .env")
    } else {
        ensureTracerInitialized()
    }
    return apiKey
}

/**
 * Wraps an AI service call so that a daily quota exhaustion skips the remainder
 * of the test gracefully by throwing [QuotaExhaustedException].
 *
 * Usage (inside a Kotest test body):
 * ```kotlin
 * val events = skipIfQuotaExhausted("generateCalendarEvents") {
 *     aiService.generateCalendarEvents(fragments)
 * }
 * ```
 */
class QuotaExhaustedException(msg: String) : org.opentest4j.TestAbortedException(msg)

suspend fun <T> TestScope.skipIfQuotaExhausted(operationName: String, block: suspend () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        if (e.message?.startsWith("QuotaExhausted") == true) {
            val msg =
                "SKIPPING $operationName: Daily Gemini quota is exhausted. Test will resume after midnight PT."
            println(msg)
            throw QuotaExhaustedException(msg)
        }
        throw e
    }
}
