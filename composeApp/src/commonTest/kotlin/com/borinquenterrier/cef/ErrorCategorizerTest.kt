package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk

class ErrorCategorizerTest : StringSpec({
    val quotaDetector = QuotaExhaustionDetector()
    val retryParser = RetryAfterParser()
    val logger = mockk<Logger>(relaxed = true)
    val categorizer = ErrorCategorizer(quotaDetector, retryParser, logger)

    "categorizes 401 Unauthorized" {
        val result = categorizer.categorizeError(HttpStatusCode.Unauthorized, "")
        result shouldBe ErrorCategorizer.ErrorType.Unauthorized
    }

    "categorizes 403 Forbidden" {
        val result = categorizer.categorizeError(HttpStatusCode.Forbidden, "")
        result shouldBe ErrorCategorizer.ErrorType.Forbidden
    }

    "categorizes 404 as StructuralError" {
        val result = categorizer.categorizeError(HttpStatusCode.NotFound, "Model not found")
        result shouldBe ErrorCategorizer.ErrorType.StructuralError("Model not found (404)")
    }

    "categorizes 400 with response modalities as StructuralError" {
        val body = "Error: response modalities not supported"
        val result = categorizer.categorizeError(HttpStatusCode.BadRequest, body)
        result shouldBe ErrorCategorizer.ErrorType.StructuralError("Response modalities not supported")
    }

    "categorizes 400 with API_KEY_INVALID as Unauthorized" {
        val body = """{"error":{"code":400,"status":"INVALID_ARGUMENT","reason":"API_KEY_INVALID"}}"""
        val result = categorizer.categorizeError(HttpStatusCode.BadRequest, body)
        result shouldBe ErrorCategorizer.ErrorType.Unauthorized
    }

    "categorizes 400 without modalities as OtherError" {
        val body = "Bad request: invalid parameter"
        val result = categorizer.categorizeError(HttpStatusCode.BadRequest, body)
        result as? ErrorCategorizer.ErrorType.OtherError shouldBe ErrorCategorizer.ErrorType.OtherError(
            "API Error (400): $body"
        )
    }

    "categorizes 503 ServiceUnavailable as TransientServerError" {
        val result = categorizer.categorizeError(HttpStatusCode.ServiceUnavailable, "")
        result shouldBe ErrorCategorizer.ErrorType.TransientServerError
    }

    "categorizes 5xx as TransientServerError" {
        val result = categorizer.categorizeError(HttpStatusCode.InternalServerError, "")
        result shouldBe ErrorCategorizer.ErrorType.TransientServerError
    }

    "categorizes 429 quota exhaustion" {
        val body = "Daily quota exhausted"
        val result = categorizer.categorizeError(HttpStatusCode.TooManyRequests, body)
        result shouldBe ErrorCategorizer.ErrorType.QuotaExhausted
    }

    "categorizes 429 transient rate limit" {
        val body = "Please retry after 60 seconds"
        val result = categorizer.categorizeError(HttpStatusCode.TooManyRequests, body)
        result shouldBe ErrorCategorizer.ErrorType.TransientRateLimit(60000L)
    }

    "rejects success status with critical error (defensive check)" {
        val result =
            categorizer.categorizeError(HttpStatusCode.OK, "Unexpected success with error body")
        result as? ErrorCategorizer.ErrorType.OtherError shouldBe ErrorCategorizer.ErrorType.OtherError(
            "Critical: Success status reached error categorizer. This indicates a bug in request handling."
        )
    }

    "categorizes unhandled error codes" {
        val body = "Some error"
        val result = categorizer.categorizeError(HttpStatusCode.ExpectationFailed, body)
        result as? ErrorCategorizer.ErrorType.OtherError shouldBe ErrorCategorizer.ErrorType.OtherError(
            "API Error (417): $body"
        )
    }

    "correctly delegates quota detection to detector" {
        val bodyWithRetryHint = "quota exceeded. retry in 60"
        val result = categorizer.categorizeError(HttpStatusCode.TooManyRequests, bodyWithRetryHint)
        result shouldBe ErrorCategorizer.ErrorType.TransientRateLimit(60000L)
    }

    "correctly delegates retry parsing to parser" {
        val bodyWithCustomDelay = "retry after 123 seconds"
        val result =
            categorizer.categorizeError(HttpStatusCode.TooManyRequests, bodyWithCustomDelay)
        result shouldBe ErrorCategorizer.ErrorType.TransientRateLimit(123000L)
    }
})
