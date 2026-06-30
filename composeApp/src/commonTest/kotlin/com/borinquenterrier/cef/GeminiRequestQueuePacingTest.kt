package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Pacing + isolation behavior for the four per-family queues.
 *
 * The effective post-slot delay is computed deterministically here (no wall-clock waits) so
 * these tests stay fast and non-flaky; the actual `delay()` mechanism is covered by the
 * timing tests in [GeminiRequestQueueTest].
 */
class GeminiRequestQueuePacingTest : FunSpec({

    // ── per-family registry / isolation ──────────────────────────────────────

    test("forFamily returns a stable singleton per family, distinct across families") {
        GeminiRequestQueue.forFamily(PromptFamily.EVENT_EXTRACTION) shouldBe
            GeminiRequestQueue.forFamily(PromptFamily.EVENT_EXTRACTION)
        GeminiRequestQueue.forFamily(PromptFamily.EVENT_EXTRACTION) shouldNotBe
            GeminiRequestQueue.forFamily(PromptFamily.CHAT)
    }

    test("each family queue knows its family and there are four of them") {
        GeminiRequestQueue.forFamily(PromptFamily.STUDY_PLAN).family shouldBe PromptFamily.STUDY_PLAN
        GeminiRequestQueue.all().size shouldBe 4
    }

    // ── per-model pacing ─────────────────────────────────────────────────────

    test("a fresh queue paces at its configured floor") {
        val queue = GeminiRequestQueue(intervalMs = 0L)
        queue.currentIntervalMs shouldBe 0L
        queue.computeEffectiveDelayMs() shouldBe 0L
    }

    test("noteModel paces to the model's interval and takes the slowest model noted in a slot") {
        val queue = GeminiRequestQueue(intervalMs = 0L)
        queue.noteModel("gemini-2.5-flash-lite") // 4000ms
        queue.noteModel("gemini-2.5-pro")        // 12000ms — slower, should win
        queue.currentIntervalMs shouldBe 12_000L
        queue.computeEffectiveDelayMs() shouldBe 12_000L
    }

    test("a reported rate-limit delay extends pacing beyond the model interval") {
        val queue = GeminiRequestQueue(intervalMs = 0L)
        queue.noteModel("gemini-2.5-flash") // 6000ms
        queue.notifyRateLimit(20_000L)
        queue.computeEffectiveDelayMs() shouldBe 20_000L
    }

    test("the configured floor wins when it exceeds the model interval") {
        val queue = GeminiRequestQueue(intervalMs = 30_000L)
        queue.noteModel("gemini-2.5-flash") // 6000ms
        queue.currentIntervalMs shouldBe 30_000L
    }

    test("resetSlotPacing clears model + rate-limit pacing back to the floor") {
        val queue = GeminiRequestQueue(intervalMs = 0L)
        queue.noteModel("gemini-2.5-pro")
        queue.notifyRateLimit(20_000L)
        queue.resetSlotPacing()
        queue.computeEffectiveDelayMs() shouldBe 0L
    }
})
