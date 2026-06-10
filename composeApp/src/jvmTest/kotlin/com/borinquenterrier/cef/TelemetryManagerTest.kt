package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TelemetryManagerTest : FunSpec({

    test("TelemetryManager should track JSON errors correctly") {
        val settings = MapSettings()
        val telemetry = TelemetryManager(settings)

        telemetry.getJsonErrors() shouldBe 0

        telemetry.logJsonError()
        telemetry.logJsonError()

        telemetry.getJsonErrors() shouldBe 2
    }

    test("TelemetryManager should track rate limit errors correctly") {
        val settings = MapSettings()
        val telemetry = TelemetryManager(settings)

        telemetry.getRateLimitErrors() shouldBe 0

        telemetry.logRateLimitError()

        telemetry.getRateLimitErrors() shouldBe 1
    }

    test("TelemetryManager should calculate Critic trigger rates correctly") {
        val settings = MapSettings()
        val telemetry = TelemetryManager(settings)

        telemetry.getCriticTotal() shouldBe 0
        telemetry.getCriticModified() shouldBe 0
        telemetry.getCriticTriggerRate() shouldBe 0.0

        telemetry.logCriticPass(modified = true)
        telemetry.logCriticPass(modified = false)
        telemetry.logCriticPass(modified = true)
        telemetry.logCriticPass(modified = false)

        telemetry.getCriticTotal() shouldBe 4
        telemetry.getCriticModified() shouldBe 2
        telemetry.getCriticTriggerRate() shouldBe 0.5
    }

    test("TelemetryManager should clear metrics on clear") {
        val settings = MapSettings()
        val telemetry = TelemetryManager(settings)

        telemetry.logJsonError()
        telemetry.logRateLimitError()
        telemetry.logCriticPass(modified = true)

        telemetry.clear()

        telemetry.getJsonErrors() shouldBe 0
        telemetry.getRateLimitErrors() shouldBe 0
        telemetry.getCriticTotal() shouldBe 0
        telemetry.getCriticModified() shouldBe 0
    }
})
