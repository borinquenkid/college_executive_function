package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProgressFractionTest : FunSpec({
    test("parses a multi-step (i/N) counter into a fraction") {
        ProgressFraction.parse("Extracting events from pages 3–4 (2/5)…") shouldBe 0.4f
        ProgressFraction.parse("section 3 (3/4)...") shouldBe 0.75f
    }
    test("returns null for a single-step (1/1) — not meaningful progress") {
        ProgressFraction.parse("section 1 (1/1)...") shouldBe null
    }
    test("returns null when there is no counter") {
        ProgressFraction.parse("Auditing source for ambiguities...") shouldBe null
        ProgressFraction.parse("Using cached analysis.") shouldBe null
    }
    test("guards against a zero total") {
        ProgressFraction.parse("weird (3/0)") shouldBe null
    }
    test("clamps an overshoot to 1.0") {
        ProgressFraction.parse("(7/5)") shouldBe 1.0f
    }
})
