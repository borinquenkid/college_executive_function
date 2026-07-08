package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChatBudgetAllocatorTest : FunSpec({

    test("historyBudget subtracts reserved output, scaffolding, sources, summary, and question") {
        val budget = ChatBudgetAllocator.historyBudget(
            contextWindow = 10_000,
            sourceBlocksTokens = 3_000,
            summaryTokens = 500,
            questionTokens = 100
        )

        val reserved = ChatBudgetAllocator.RESERVED_OUTPUT_TOKENS +
            ChatBudgetAllocator.SYSTEM_INSTRUCTIONS_TOKENS
        budget shouldBe (10_000 - reserved - 3_000 - 500 - 100)
    }

    test("historyBudget never goes negative when reserved costs exceed the window") {
        val budget = ChatBudgetAllocator.historyBudget(
            contextWindow = 1_000,
            sourceBlocksTokens = 5_000,
            summaryTokens = 5_000,
            questionTokens = 5_000
        )

        budget shouldBe 0
    }

    test("historyBudget grows with a larger context window, all else equal") {
        val small = ChatBudgetAllocator.historyBudget(10_000, 1_000, 0, 100)
        val large = ChatBudgetAllocator.historyBudget(100_000, 1_000, 0, 100)

        (large > small) shouldBe true
    }
})
