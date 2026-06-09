package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class QuotaExhaustionDetectorTest : StringSpec({
    val detector = QuotaExhaustionDetector()

    "detects quota exhausted with standard message" {
        val body = "Your daily quota is exhausted"
        detector.isQuotaExhausted(body) shouldBe true
    }

    "detects quota exceeded" {
        val body = "Quota limit exceeded"
        detector.isQuotaExhausted(body) shouldBe true
    }

    "detects quota exhausted (case insensitive)" {
        val body = "YOUR DAILY QUOTA IS EXHAUSTED"
        detector.isQuotaExhausted(body) shouldBe true
    }

    "rejects transient rate limit with 'retry in' hint" {
        val body = "Quota limit exhausted. Please retry in 60 seconds"
        detector.isQuotaExhausted(body) shouldBe false
    }

    "rejects error without quota keyword" {
        val body = "Rate limit exceeded - please wait before retrying"
        detector.isQuotaExhausted(body) shouldBe false
    }

    "rejects error without exhaustion keyword" {
        val body = "Quota-related error occurred"
        detector.isQuotaExhausted(body) shouldBe false
    }

    "accepts 'exceeded' as exhaustion keyword" {
        val body = "Quota exceeded"
        detector.isQuotaExhausted(body) shouldBe true
    }

    "accepts 'limit' as exhaustion keyword" {
        val body = "Daily quota limit"
        detector.isQuotaExhausted(body) shouldBe true
    }

    "rejects empty body" {
        detector.isQuotaExhausted("") shouldBe false
    }

    "handles complex quota messages" {
        val body = "Error 429: Daily quota for model gemini-pro exhausted. Next reset: 2025-12-31T23:59:59Z"
        detector.isQuotaExhausted(body) shouldBe true
    }
})
