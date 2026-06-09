package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RetryAfterParserTest : StringSpec({
    val parser = RetryAfterParser()

    "extracts numeric retry value" {
        val body = "Please retry after 5 seconds"
        parser.extractRetryAfterMs(body) shouldBe 5000L
    }

    "extracts single digit retry value" {
        val body = "Retry after 1 second"
        parser.extractRetryAfterMs(body) shouldBe 1000L
    }

    "extracts large retry value" {
        val body = "Rate limit: retry after 3600 seconds"
        parser.extractRetryAfterMs(body) shouldBe 3600000L
    }

    "handles case insensitive 'retry'" {
        val body = "RETRY AFTER 10 SECONDS"
        parser.extractRetryAfterMs(body) shouldBe 10000L
    }

    "handles 'Retry' capitalization" {
        val body = "Retry after 30 seconds please"
        parser.extractRetryAfterMs(body) shouldBe 30000L
    }

    "extracts value with various separators" {
        val body = "Please retry-after 7 seconds"
        parser.extractRetryAfterMs(body) shouldBe 7000L
    }

    "extracts value with colon separator" {
        val body = "retry: 15 seconds"
        parser.extractRetryAfterMs(body) shouldBe 15000L
    }

    "defaults to 1000ms when no number found" {
        val body = "Please retry later"
        parser.extractRetryAfterMs(body) shouldBe 1000L
    }

    "defaults to 1000ms for empty string" {
        parser.extractRetryAfterMs("") shouldBe 1000L
    }

    "handles leading whitespace before number" {
        val body = "retry     120 seconds"
        parser.extractRetryAfterMs(body) shouldBe 120000L
    }

    "ignores numbers before 'retry' keyword" {
        val body = "Code 429: wait. Retry after 45 seconds"
        parser.extractRetryAfterMs(body) shouldBe 45000L
    }

    "uses first match when multiple numbers after retry" {
        val body = "retry after 20 then 30 seconds"
        parser.extractRetryAfterMs(body) shouldBe 20000L
    }

    "handles zero value (edge case)" {
        val body = "retry after 0 seconds"
        parser.extractRetryAfterMs(body) shouldBe 0L
    }
})
