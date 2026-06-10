package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify

class GeminiErrorHandlerTest : StringSpec({

    "categorizeError detects unauthorized status" {
        val categorizer = ErrorCategorizer(
            QuotaExhaustionDetector(),
            RetryAfterParser(),
            mockk<Logger>(relaxed = true)
        )
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(categorizer, modelNegotiator, logger)

        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.Unauthorized, "")

        result shouldBe ErrorCategorizer.ErrorType.Unauthorized
    }

    "categorizeError detects forbidden status" {
        val categorizer = ErrorCategorizer(
            QuotaExhaustionDetector(),
            RetryAfterParser(),
            mockk<Logger>(relaxed = true)
        )
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(categorizer, modelNegotiator, logger)

        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.Forbidden, "")

        result shouldBe ErrorCategorizer.ErrorType.Forbidden
    }

    "categorizeError detects structural errors" {
        val categorizer = ErrorCategorizer(
            QuotaExhaustionDetector(),
            RetryAfterParser(),
            mockk<Logger>(relaxed = true)
        )
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(categorizer, modelNegotiator, logger)

        val result =
            handler.categorizeError(io.ktor.http.HttpStatusCode.NotFound, "Model not found")

        result as? ErrorCategorizer.ErrorType.StructuralError shouldBe ErrorCategorizer.ErrorType.StructuralError(
            "Model not found (404)"
        )
    }

    "categorizeError detects quota exhausted" {
        val categorizer = ErrorCategorizer(
            QuotaExhaustionDetector(),
            RetryAfterParser(),
            mockk<Logger>(relaxed = true)
        )
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(categorizer, modelNegotiator, logger)

        val body = "Your daily quota is exhausted"
        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.TooManyRequests, body)

        result shouldBe ErrorCategorizer.ErrorType.QuotaExhausted
    }

    "categorizeError detects transient rate limit" {
        val categorizer = ErrorCategorizer(
            QuotaExhaustionDetector(),
            RetryAfterParser(),
            mockk<Logger>(relaxed = true)
        )
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(categorizer, modelNegotiator, logger)

        val body = "Please retry after 5 seconds"
        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.TooManyRequests, body)

        result as? ErrorCategorizer.ErrorType.TransientRateLimit shouldBe ErrorCategorizer.ErrorType.TransientRateLimit(
            5000L
        )
    }

    "handleStructuralError blacklists model" {
        val categorizer = ErrorCategorizer(
            QuotaExhaustionDetector(),
            RetryAfterParser(),
            mockk<Logger>(relaxed = true)
        )
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(categorizer, modelNegotiator, logger)

        handler.handleStructuralError("gemini-pro")

        verify { modelNegotiator.blacklistModel("gemini-pro") }
        verify { modelNegotiator.evictFromCache("gemini-pro") }
    }
})
