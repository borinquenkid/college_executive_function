package com.borinquenterrier.cef

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
 * Reads the Gemini API key from the local `.env` file.
 * Returns null (and prints a skip message) if no key is found.
 */
fun resolveApiKey(testName: String): String? {
    val envFile = listOf(
        java.io.File("../.env"),
        java.io.File(".env")
    ).find { it.exists() }

    val envMap = envFile?.readLines()?.associate {
        val key = it.substringBefore("=").trim()
        val value = it.substringAfter("=").trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        key to value
    } ?: emptyMap()

    val apiKey = (envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"])
        ?.takeIf { it.isNotBlank() }

    if (apiKey == null) {
        println("SKIPPING $testName: No Gemini API Key found in .env")
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
class QuotaExhaustedException(msg: String) : Exception(msg)

suspend fun <T> TestScope.skipIfQuotaExhausted(operationName: String, block: suspend () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        if (e.message?.startsWith("QuotaExhausted") == true) {
            val msg = "SKIPPING $operationName: Daily Gemini quota is exhausted. Test will resume after midnight PT."
            println(msg)
            throw QuotaExhaustedException(msg)
        }
        throw e
    }
}
