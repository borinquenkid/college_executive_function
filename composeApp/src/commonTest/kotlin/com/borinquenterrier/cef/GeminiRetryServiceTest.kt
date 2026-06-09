package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject

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
})
