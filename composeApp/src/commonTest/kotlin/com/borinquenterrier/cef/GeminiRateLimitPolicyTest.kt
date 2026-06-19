package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class GeminiRateLimitPolicyTest : FunSpec({

    val model = "gemini-pro"

    // ── ExtremeDelay (>120s) ──────────────────────────────────────────────────

    test("delay > 120s returns ExtremeDelay") {
        val d = GeminiRateLimitPolicy.decide(121_000L, 0, 0, model)
        d.shouldBeInstanceOf<GeminiRateLimitPolicy.Decision.ExtremeDelay>()
    }

    test("ExtremeDelay does not advance attempt when extreme count < 10") {
        val d = GeminiRateLimitPolicy.decide(121_000L, 5, 0, model) as GeminiRateLimitPolicy.Decision.ExtremeDelay
        d.advanceAttempt shouldBe false
    }

    test("ExtremeDelay advances attempt when extreme count reaches 9 (next would be 10)") {
        val d = GeminiRateLimitPolicy.decide(121_000L, 9, 0, model) as GeminiRateLimitPolicy.Decision.ExtremeDelay
        d.advanceAttempt shouldBe true
    }

    test("ExtremeDelay error message includes model name and delay seconds") {
        val d = GeminiRateLimitPolicy.decide(180_000L, 0, 0, model) as GeminiRateLimitPolicy.Decision.ExtremeDelay
        d.errorMessage.contains(model) shouldBe true
        d.errorMessage.contains("180") shouldBe true // 180_000ms / 1000 = 180s
    }

    // ── SaturatedKey (>10s, ≥2 consecutive) ──────────────────────────────────

    test("delay > 10s with consecutiveRateLimitCount=1 returns SaturatedKey") {
        // After this call, count would become 2 → saturated
        val d = GeminiRateLimitPolicy.decide(15_000L, 0, 1, model)
        d.shouldBeInstanceOf<GeminiRateLimitPolicy.Decision.SaturatedKey>()
    }

    test("SaturatedKey hold delay equals delayMs") {
        val d = GeminiRateLimitPolicy.decide(15_000L, 0, 1, model) as GeminiRateLimitPolicy.Decision.SaturatedKey
        d.holdDelayMs shouldBe 15_000L
    }

    test("SaturatedKey blacklist duration is delayMs + 2000") {
        val d = GeminiRateLimitPolicy.decide(20_000L, 0, 1, model) as GeminiRateLimitPolicy.Decision.SaturatedKey
        d.blacklistDurationMs shouldBe 22_000L
    }

    // ── LongDelay (>10s, first occurrence) ───────────────────────────────────

    test("delay > 10s with consecutiveRateLimitCount=0 returns LongDelay") {
        val d = GeminiRateLimitPolicy.decide(15_000L, 0, 0, model)
        d.shouldBeInstanceOf<GeminiRateLimitPolicy.Decision.LongDelay>()
    }

    test("LongDelay blacklist duration is delayMs + 2000") {
        val d = GeminiRateLimitPolicy.decide(11_000L, 0, 0, model) as GeminiRateLimitPolicy.Decision.LongDelay
        d.blacklistDurationMs shouldBe 13_000L
    }

    test("LongDelay error message includes model name") {
        val d = GeminiRateLimitPolicy.decide(11_000L, 0, 0, model) as GeminiRateLimitPolicy.Decision.LongDelay
        d.errorMessage.contains(model) shouldBe true
    }

    // ── ShortDelay (≤10s) ─────────────────────────────────────────────────────

    test("delay <= 10s returns ShortDelay") {
        val d = GeminiRateLimitPolicy.decide(5_000L, 0, 0, model)
        d.shouldBeInstanceOf<GeminiRateLimitPolicy.Decision.ShortDelay>()
    }

    test("ShortDelay carries the original delayMs") {
        val d = GeminiRateLimitPolicy.decide(3_000L, 0, 0, model) as GeminiRateLimitPolicy.Decision.ShortDelay
        d.delayMs shouldBe 3_000L
    }

    test("exactly 10s delay is ShortDelay (boundary)") {
        val d = GeminiRateLimitPolicy.decide(10_000L, 0, 0, model)
        d.shouldBeInstanceOf<GeminiRateLimitPolicy.Decision.ShortDelay>()
    }

    test("exactly 120s delay is LongDelay not ExtremeDelay (boundary)") {
        val d = GeminiRateLimitPolicy.decide(120_000L, 0, 0, model)
        d.shouldBeInstanceOf<GeminiRateLimitPolicy.Decision.LongDelay>()
    }
})
