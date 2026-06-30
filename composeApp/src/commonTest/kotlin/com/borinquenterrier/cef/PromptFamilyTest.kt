package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

/**
 * There are exactly four prompt families — mirroring the four AiPrompts builder objects
 * (EventBuilder, StudyPlanBuilder, CategorizationBuilder, ChatBuilder). Each gets its own
 * queue, so a slowdown in one family cannot starve the others.
 */
class PromptFamilyTest : FunSpec({

    test("there are exactly four prompt families") {
        PromptFamily.entries.size shouldBe 4
    }

    test("each family has a distinct, stable queue name") {
        PromptFamily.entries.map { it.queueName }.shouldContainExactlyInAnyOrder(
            "event", "study_plan", "categorization", "chat"
        )
    }
})
