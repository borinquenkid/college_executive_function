package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pacing is "for the model itself": each model has a requests-per-minute ceiling that
 * the per-family queue uses to space its calls. These tests pin the RPM table and the
 * RPM → minimum-interval math.
 */
class ModelRpmTest : FunSpec({

    test("rpmFor returns the configured ceiling for known models") {
        ModelRpm.rpmFor("gemini-2.5-pro") shouldBe 5
        ModelRpm.rpmFor("gemini-2.5-flash") shouldBe 10
        ModelRpm.rpmFor("gemini-2.5-flash-lite") shouldBe 15
    }

    test("rpmFor falls back to DEFAULT_RPM for an unknown model") {
        ModelRpm.rpmFor("totally-made-up-model") shouldBe ModelRpm.DEFAULT_RPM
    }

    test("rpmFor matches by longest known prefix for versioned/preview model ids") {
        // An unlisted preview variant should inherit its base family's ceiling,
        // not collapse to the default.
        ModelRpm.rpmFor("gemini-2.5-flash-preview-09-2025") shouldBe 10
        ModelRpm.rpmFor("gemini-2.5-flash-lite-preview-09-2025") shouldBe 15
    }

    test("intervalMsFor converts RPM to a minimum inter-request spacing") {
        ModelRpm.intervalMsFor("gemini-2.5-flash") shouldBe 6_000L      // 60000 / 10
        ModelRpm.intervalMsFor("gemini-2.5-pro") shouldBe 12_000L       // 60000 / 5
        ModelRpm.intervalMsFor("gemini-2.5-flash-lite") shouldBe 4_000L // 60000 / 15
    }

    test("intervalMsFor never divides by zero for a degenerate RPM") {
        // Even if a model is mapped to a nonsensical value, spacing stays finite.
        ModelRpm.intervalMsFor("totally-made-up-model") shouldBe 60_000L / ModelRpm.DEFAULT_RPM
    }
})
