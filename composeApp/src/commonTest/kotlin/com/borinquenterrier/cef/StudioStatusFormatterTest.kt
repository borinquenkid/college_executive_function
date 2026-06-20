package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class StudioStatusFormatterTest : FunSpec({

    test("returns statusMessage unchanged when not loading") {
        StudioStatusFormatter.format("Idle", isLoading = false, pendingRequests = 5, remainingSeconds = 30) shouldBe "Idle"
    }

    test("returns statusMessage unchanged when loading with 1 request") {
        StudioStatusFormatter.format("Processing…", isLoading = true, pendingRequests = 1, remainingSeconds = 10) shouldBe "Processing…"
    }

    test("appends queue count when loading with multiple requests") {
        val result = StudioStatusFormatter.format("Extracting", isLoading = true, pendingRequests = 3, remainingSeconds = 0)
        result shouldContain "3 requests queued"
        result shouldContain "Extracting"
    }

    test("includes minutes and seconds when remaining >= 60") {
        val result = StudioStatusFormatter.format("Extracting", isLoading = true, pendingRequests = 4, remainingSeconds = 90)
        result shouldContain "~1m 30s remaining"
    }

    test("includes seconds only when remaining < 60 and > 0") {
        val result = StudioStatusFormatter.format("Extracting", isLoading = true, pendingRequests = 2, remainingSeconds = 45)
        result shouldContain "~45s remaining"
    }

    test("omits time part when remaining is 0") {
        val result = StudioStatusFormatter.format("Extracting", isLoading = true, pendingRequests = 2, remainingSeconds = 0)
        result shouldNotContain "remaining"
        result shouldContain "2 requests queued"
    }

    test("exactly 60 seconds formats as 1m 0s") {
        val result = StudioStatusFormatter.format("X", isLoading = true, pendingRequests = 2, remainingSeconds = 60)
        result shouldContain "~1m 0s remaining"
    }
})
