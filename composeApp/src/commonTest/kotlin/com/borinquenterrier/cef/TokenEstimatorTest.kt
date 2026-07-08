package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TokenEstimatorTest : FunSpec({

    test("estimate returns zero for an empty string") {
        TokenEstimator.estimate("") shouldBe 0
    }

    test("estimate rounds up so a short non-empty string is never zero") {
        TokenEstimator.estimate("hi") shouldBe 1
    }

    test("estimate applies the chars/4 heuristic") {
        TokenEstimator.estimate("x".repeat(400)) shouldBe 100
    }

    test("estimate rounds a partial token up") {
        TokenEstimator.estimate("x".repeat(401)) shouldBe 101
    }

    test("estimate is monotonically non-decreasing as text grows") {
        val shortEstimate = TokenEstimator.estimate("x".repeat(40))
        val longEstimate = TokenEstimator.estimate("x".repeat(4000))
        (longEstimate > shortEstimate) shouldBe true
    }
})
