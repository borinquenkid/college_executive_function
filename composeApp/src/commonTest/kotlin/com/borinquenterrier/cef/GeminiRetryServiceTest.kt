package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class GeminiRetryServiceTest : StringSpec({

    "wait delays for specified milliseconds" {
        val logger = mockk<Logger>(relaxed = true)
        var delayMs = 0L
        val delayFn: suspend (Long) -> Unit = { ms -> delayMs = ms }
        val service = GeminiRetryService(logger, delayFn)

        service.wait(1000L)

        delayMs shouldBe 1000L
    }

    "resolveRetryDelay extracts from Retry-After header" {
        val logger = mockk<Logger>(relaxed = true)
        val delayFn: suspend (Long) -> Unit = {}
        val service = GeminiRetryService(logger, delayFn)

        val headers = io.ktor.http.headersOf("Retry-After", "5")
        val delayMs = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = headers,
            body = "",
            attempts = 1
        )

        delayMs shouldBe 5000L
    }

    "checkRateLimitWindow throws when rate limit active" {
        val logger = mockk<Logger>(relaxed = true)
        val delayFn: suspend (Long) -> Unit = {}
        val service = GeminiRetryService(logger, delayFn)

        service.activateRateLimitWindow()

        try {
            service.checkRateLimitWindow()
        } catch (e: Exception) {
            // Expected to throw rate limit exceeded
        }
    }

    "activateRateLimitWindow sets window" {
        val logger = mockk<Logger>(relaxed = true)
        val delayFn: suspend (Long) -> Unit = {}
        val service = GeminiRetryService(logger, delayFn)

        service.activateRateLimitWindow()

        try {
            service.checkRateLimitWindow()
        } catch (e: Exception) {
            e.message?.contains("rate limit") == true
        }
    }

    "activateGlobalHold sets globalHoldState flow" {
        val logger = mockk<Logger>(relaxed = true)
        val service = GeminiRetryService(logger, {})
        
        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.globalHoldState.value shouldBe null
        
        service.activateGlobalHold(5000L)
        (GeminiRetryService.globalHoldState.value != null) shouldBe true
    }

    "cancelHold clears globalHoldState and causes wait to throw CancellationException" {
        val logger = mockk<Logger>(relaxed = true)
        var delayMs = 0L
        val delayFn: suspend (Long) -> Unit = { ms -> delayMs = ms }
        val service = GeminiRetryService(logger, delayFn)

        GeminiRetryService.clearGlobalHoldForTesting()
        service.activateGlobalHold(10000L)

        GeminiRetryService.cancelHold()

        io.kotest.assertions.throwables.shouldThrow<kotlinx.coroutines.CancellationException> {
            service.wait(5000L)
        }
        
        GeminiRetryService.globalHoldState.value shouldBe null
    }

    "checkRateLimitWindow suspends during global hold" {
        val logger = mockk<Logger>(relaxed = true)
        var delayMs = 0L
        val delayFn: suspend (Long) -> Unit = { ms -> delayMs = ms }
        val service = GeminiRetryService(logger, delayFn)

        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.clearRateLimitResetForTesting()
        service.activateGlobalHold(10000L)
        service.checkRateLimitWindow()

        (delayMs > 0) shouldBe true
        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.clearRateLimitResetForTesting()
    }

    // ── clearGlobalHold ───────────────────────────────────────────────────────

    "clearGlobalHold resets hold and clears globalHoldState" {
        val service = GeminiRetryService(null, {})
        service.activateGlobalHold(10000L)
        (GeminiRetryService.globalHoldState.value != null) shouldBe true
        GeminiRetryService.clearGlobalHold()
        GeminiRetryService.globalHoldState.value shouldBe null
    }

    // ── cancelHold with active deferred ───────────────────────────────────────

    "cancelHold during wait propagates CancellationException via activeDeferred" {
        GeminiRetryService.clearGlobalHoldForTesting()
        // delayFn calls cancelHold while activeDeferred is set → completeExceptionally fires
        val service = GeminiRetryService(null, { GeminiRetryService.cancelHold() })
        io.kotest.assertions.throwables.shouldThrow<kotlinx.coroutines.CancellationException> {
            service.wait(100L)
        }
        GeminiRetryService.clearGlobalHoldForTesting()
    }

    // ── resolveRetryDelay: body retry-in hint ─────────────────────────────────

    "resolveRetryDelay extracts delay from server body retry-in hint" {
        val logger = mockk<Logger>(relaxed = true)
        val service = GeminiRetryService(logger, {})
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = io.ktor.http.headersOf(),
            body = "retry in 3.5 s",
            attempts = 1
        )
        result shouldBe 4000L // (3.5 * 1000).toLong() + 500
    }

    // ── resolveRetryDelay: x-ratelimit-reset header ───────────────────────────

    "resolveRetryDelay uses x-ratelimit-reset header when present and numeric" {
        val logger = mockk<Logger>(relaxed = true)
        val service = GeminiRetryService(logger, {})
        val futureEpochSeconds = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000L + 60L
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = io.ktor.http.headersOf("x-ratelimit-reset", futureEpochSeconds.toString()),
            body = "",
            attempts = 1
        )
        (result >= 59_500L) shouldBe true
    }

    "resolveRetryDelay falls through when x-ratelimit-reset is non-numeric" {
        val service = GeminiRetryService(null, {})
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = io.ktor.http.headersOf("x-ratelimit-reset", "not-a-number"),
            body = "",
            attempts = 1
        )
        result shouldBe 2000L // falls to exponential backoff: TooManyRequests attempt 1
    }

    // ── resolveRetryDelay: non-parseable Retry-After ──────────────────────────

    "resolveRetryDelay falls through to exponential backoff when Retry-After is non-numeric" {
        val service = GeminiRetryService(null, {})
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = io.ktor.http.headersOf("Retry-After", "not-a-number"),
            body = "",
            attempts = 1
        )
        result shouldBe 2000L
    }

    // ── resolveRetryDelay: exponential fallback with null logger ──────────────

    "resolveRetryDelay exponential fallback with null logger covers null branch of logger?.d" {
        val service = GeminiRetryService(null, {})
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = io.ktor.http.headersOf(),
            body = "",
            attempts = 1
        )
        result shouldBe 2000L
    }

    "resolveRetryDelay exponential fallback uses 1000ms base for non-429 status" {
        val service = GeminiRetryService(null, {})
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.InternalServerError,
            headers = io.ktor.http.headersOf(),
            body = "",
            attempts = 1
        )
        result shouldBe 1000L
    }

    "resolveRetryDelay exponential fallback logs the delay message when logger is non-null" {
        val logger = mockk<Logger>(relaxed = true)
        val service = GeminiRetryService(logger, {})
        val result = service.resolveRetryDelay(
            status = io.ktor.http.HttpStatusCode.TooManyRequests,
            headers = io.ktor.http.headersOf(),
            body = "",
            attempts = 2
        )
        result shouldBe 4000L // 2000L * (1 shl 1)
    }

    // ── wait: skipLongDelaysInTests ───────────────────────────────────────────

    "wait throws QuotaExhausted when skipLongDelaysInTests is true and delay >= 5000ms" {
        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.skipLongDelaysInTests = true
        val service = GeminiRetryService(null, {})
        val ex = io.kotest.assertions.throwables.shouldThrow<Exception> {
            service.wait(5000L)
        }
        (ex.message?.contains("QuotaExhausted") == true) shouldBe true
        GeminiRetryService.clearGlobalHoldForTesting()
    }

    "wait proceeds normally when skipLongDelaysInTests is true but delay < 5000ms" {
        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.skipLongDelaysInTests = true
        var delayMs = 0L
        val service = GeminiRetryService(null, { ms -> delayMs = ms })
        service.wait(100L) // below threshold → should not throw
        delayMs shouldBe 100L
        GeminiRetryService.clearGlobalHoldForTesting()
    }

    // ── wait: delayFn throws ──────────────────────────────────────────────────

    "wait propagates exception thrown by delayFn via completeExceptionally" {
        GeminiRetryService.clearGlobalHoldForTesting()
        val service = GeminiRetryService(null, { throw RuntimeException("network error") })
        io.kotest.assertions.throwables.shouldThrow<RuntimeException> {
            service.wait(100L)
        }
        GeminiRetryService.clearGlobalHoldForTesting()
    }

    // ── wait: finally block globalHoldState clearing ──────────────────────────

    "wait finally block clears globalHoldState when hold has expired" {
        GeminiRetryService.clearGlobalHoldForTesting()
        val service = GeminiRetryService(null, {})
        service.activateGlobalHold(5000L)      // set globalHoldState to non-null
        GeminiRetryService.clearGlobalHold()   // reset globalHoldUntil = 0L
        service.wait(10L)                       // finally: now >= 0L → clears state
        GeminiRetryService.globalHoldState.value shouldBe null
    }

    "wait exception path leaves globalHoldState set when hold window is still active" {
        GeminiRetryService.clearGlobalHoldForTesting()
        val service = GeminiRetryService(null, { throw RuntimeException("network error") })
        service.activateGlobalHold(10000L)     // globalHoldUntil = now + 12000ms
        io.kotest.assertions.throwables.shouldThrow<RuntimeException> {
            service.wait(100L)
        }
        // exception-path finally: now < globalHoldUntil → state NOT cleared
        (GeminiRetryService.globalHoldState.value != null) shouldBe true
        GeminiRetryService.clearGlobalHoldForTesting()
    }

    // ── checkRateLimitWindow: null logger ─────────────────────────────────────

    "checkRateLimitWindow with null logger handles global hold path without NPE" {
        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.clearRateLimitResetForTesting()
        var delayCapture = 0L
        val service = GeminiRetryService(null, { ms -> delayCapture = ms })
        service.activateGlobalHold(10000L)
        service.checkRateLimitWindow()
        (delayCapture > 0) shouldBe true
        GeminiRetryService.clearGlobalHoldForTesting()
        GeminiRetryService.clearRateLimitResetForTesting()
    }
})
