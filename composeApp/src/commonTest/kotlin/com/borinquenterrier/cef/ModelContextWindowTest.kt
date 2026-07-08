package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Mirrors [ModelRpmTest]'s coverage shape for the sibling context-window table.
 */
class ModelContextWindowTest : FunSpec({

    test("contextWindowFor returns the configured window for known models") {
        ModelContextWindow.contextWindowFor("gemini-2.5-pro") shouldBe 1_048_576
        ModelContextWindow.contextWindowFor("gemini-2.5-flash-lite") shouldBe 1_048_576
    }

    test("contextWindowFor falls back to DEFAULT_CONTEXT_WINDOW for an unknown model") {
        ModelContextWindow.contextWindowFor("totally-made-up-model") shouldBe
            ModelContextWindow.DEFAULT_CONTEXT_WINDOW
    }

    test("contextWindowFor matches by longest known prefix for versioned/preview model ids") {
        ModelContextWindow.contextWindowFor("gemini-2.5-flash-preview-09-2025") shouldBe 1_048_576
    }

    test("conservativeChatWindow returns the minimum window across the LIGHT tier") {
        val expected = GeminiModelNegotiator.LIGHT_PREFERENCES
            .minOf { ModelContextWindow.contextWindowFor(it) }

        ModelContextWindow.conservativeChatWindow() shouldBe expected
    }
})
