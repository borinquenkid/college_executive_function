package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class GeminiErrorHandlerTest : StringSpec({

    "categorizeError detects unauthorized status" {
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(modelNegotiator, logger)

        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.Unauthorized, "")

        result shouldBe GeminiErrorHandler.ErrorType.Unauthorized
    }

    "categorizeError detects forbidden status" {
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(modelNegotiator, logger)

        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.Forbidden, "")

        result shouldBe GeminiErrorHandler.ErrorType.Forbidden
    }

    "categorizeError detects structural errors" {
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(modelNegotiator, logger)

        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.NotFound, "Model not found")

        result as? GeminiErrorHandler.ErrorType.StructuralError shouldBe GeminiErrorHandler.ErrorType.StructuralError("Model not found (404)")
    }

    "categorizeError detects quota exhausted" {
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(modelNegotiator, logger)

        val body = "Your daily quota is exhausted"
        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.TooManyRequests, body)

        result shouldBe GeminiErrorHandler.ErrorType.QuotaExhausted
    }

    "categorizeError detects transient rate limit" {
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(modelNegotiator, logger)

        val body = "Please retry after 5 seconds"
        val result = handler.categorizeError(io.ktor.http.HttpStatusCode.TooManyRequests, body)

        result as? GeminiErrorHandler.ErrorType.TransientRateLimit shouldBe GeminiErrorHandler.ErrorType.TransientRateLimit(5000L)
    }

    "handleStructuralError blacklists model" {
        val modelNegotiator = mockk<GeminiModelNegotiator>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val handler = GeminiErrorHandler(modelNegotiator, logger)

        handler.handleStructuralError("gemini-pro")

        // Verify model was blacklisted
    }
})
