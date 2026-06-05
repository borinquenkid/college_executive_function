package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.*
import kotlinx.datetime.Clock

/**
 * Unit tests for GeminiAIService.resolveRetryDelay().
 *
 * The function resolves how long to wait before a retry after a transient API error.
 * Priority order:
 *  1. Response body "retry in X.Xs"  — Gemini's actual signal
 *  2. x-ratelimit-reset epoch header — absolute reset window
 *  3. Retry-After header             — standard RFC 7231
 *  4. Exponential back-off           — no server hint available
 */
class RetryDelayResolutionTest : FunSpec({

    // Build a GeminiAIService instance wired for unit testing
    fun makeService() = GeminiAIService(apiKey = "test-key", delayFn = {})

    // Helper: build a Headers object from key-value pairs
    fun headers(vararg pairs: Pair<String, String>): Headers =
        HeadersBuilder().apply { pairs.forEach { (k, v) -> append(k, v) } }.build()

    val emptyHeaders = Headers.Empty

    // -------------------------------------------------------------------------
    // Priority 1: Body hint "retry in X.Xs"
    // -------------------------------------------------------------------------

    test("body hint 'Please retry in 17.6s' resolves to 17600ms + 500ms buffer") {
        val service = makeService()
        val body = """{"error":{"message":"RESOURCE_EXHAUSTED. Please retry in 17.6s."}}"""
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = emptyHeaders,
            body = body,
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 18100L // (17.6 * 1000).toLong() + 500
    }

    test("body hint 'retry in 30s' (integer) resolves to 30500ms") {
        val service = makeService()
        val body = "Rate limit exceeded. retry in 30s"
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = emptyHeaders,
            body = body,
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 30500L
    }

    test("body hint is case-insensitive") {
        val service = makeService()
        val body = "RETRY IN 5s"
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = emptyHeaders,
            body = body,
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 5500L
    }

    // -------------------------------------------------------------------------
    // Priority 2: x-ratelimit-reset epoch header
    // -------------------------------------------------------------------------

    test("x-ratelimit-reset epoch header computes delta from now") {
        val service = makeService()
        val resetEpoch = (Clock.System.now().toEpochMilliseconds() / 1000L) + 20L // 20s from now
        val h = headers("x-ratelimit-reset" to resetEpoch.toString())
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = h,
            body = "no body hint here",
            attempts = 1,
            tag = "test"
        )
        // Should be ~20000ms + 500ms buffer, allow ±1s for execution time
        (delay >= 19500L) shouldBe true
        (delay < 22000L) shouldBe true
    }

    test("x-ratelimit-reset in the past is coerced to at least 1s") {
        val service = makeService()
        val pastEpoch = (Clock.System.now().toEpochMilliseconds() / 1000L) - 60L // 60s ago
        val h = headers("x-ratelimit-reset" to pastEpoch.toString())
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = h,
            body = "no body hint",
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 1500L // coerceAtLeast(1) * 1000 + 500
    }

    // -------------------------------------------------------------------------
    // Priority 3: Retry-After header
    // -------------------------------------------------------------------------

    test("Retry-After header resolves to exact seconds * 1000") {
        val service = makeService()
        val h = headers("Retry-After" to "10")
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = h,
            body = "no body hint",
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 10000L
    }

    test("lowercase retry-after header is also accepted") {
        val service = makeService()
        val h = headers("retry-after" to "5")
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = h,
            body = "no body hint",
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 5000L
    }

    // -------------------------------------------------------------------------
    // Priority 4: Exponential back-off (no server hint)
    // -------------------------------------------------------------------------

    test("exponential backoff for 429 with no hints: attempt 1 = 2000ms") {
        val service = makeService()
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = emptyHeaders,
            body = "Service busy",
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 2000L
    }

    test("exponential backoff for 429 with no hints: attempt 2 = 4000ms") {
        val service = makeService()
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = emptyHeaders,
            body = "Service busy",
            attempts = 2,
            tag = "test"
        )
        delay shouldBe 4000L
    }

    test("exponential backoff for 429 with no hints: attempt 3 = 8000ms") {
        val service = makeService()
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = emptyHeaders,
            body = "Service busy",
            attempts = 3,
            tag = "test"
        )
        delay shouldBe 8000L
    }

    test("exponential backoff for 503 uses 1000ms base: attempt 1 = 1000ms") {
        val service = makeService()
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.ServiceUnavailable,
            headers = emptyHeaders,
            body = "down",
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 1000L
    }

    // -------------------------------------------------------------------------
    // Priority order: body hint wins over header
    // -------------------------------------------------------------------------

    test("body hint takes priority over Retry-After header") {
        val service = makeService()
        val h = headers("Retry-After" to "60") // would be 60s
        val body = "retry in 5s"               // should win with 5s
        val delay = service.resolveRetryDelay(
            status = HttpStatusCode.TooManyRequests,
            headers = h,
            body = body,
            attempts = 1,
            tag = "test"
        )
        delay shouldBe 5500L
    }
})
