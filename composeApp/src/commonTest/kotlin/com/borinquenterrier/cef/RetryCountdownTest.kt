package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RetryCountdownTest : FunSpec({

    test("returns null when holdUntil is in the past") {
        RetryCountdown.secondsRemaining(holdUntilMs = 1000L, nowMs = 2000L) shouldBe null
    }

    test("returns null when holdUntil equals now") {
        RetryCountdown.secondsRemaining(holdUntilMs = 1000L, nowMs = 1000L) shouldBe null
    }

    test("returns ceiling seconds when 1 ms remains") {
        RetryCountdown.secondsRemaining(holdUntilMs = 1001L, nowMs = 1000L) shouldBe 1
    }

    test("returns 1 for exactly 1000 ms remaining") {
        RetryCountdown.secondsRemaining(holdUntilMs = 2000L, nowMs = 1000L) shouldBe 1
    }

    test("returns 2 for 1001 ms remaining (ceiling)") {
        RetryCountdown.secondsRemaining(holdUntilMs = 3001L, nowMs = 2000L) shouldBe 2
    }

    test("returns 60 for exactly 60 seconds remaining") {
        RetryCountdown.secondsRemaining(holdUntilMs = 61_000L, nowMs = 1_000L) shouldBe 60
    }

    test("returns non-null for positive remaining time") {
        RetryCountdown.secondsRemaining(holdUntilMs = 5000L, nowMs = 1000L) shouldNotBe null
    }
})
