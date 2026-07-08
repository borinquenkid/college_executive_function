package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChatCompactionPlannerTest : FunSpec({

    // 40 chars => TokenEstimator.estimate == 10, so budgets below are easy to reason about.
    fun turn(label: String, createdAt: Long) =
        ChatMessage.create("x".repeat(40) + label, ChatRole.USER, createdAt)

    test("plan returns an empty plan for an empty message list") {
        val plan = ChatCompactionPlanner.plan(emptyList(), historyTokenBudget = 1_000)

        plan.verbatimTail shouldBe emptyList()
        plan.turnsToFold shouldBe emptyList()
    }

    test("plan keeps everything verbatim and folds nothing when the whole history fits") {
        val messages = (1..3).map { turn("m$it", it.toLong()) }

        val plan = ChatCompactionPlanner.plan(messages, historyTokenBudget = 100)

        plan.verbatimTail shouldBe messages
        plan.turnsToFold shouldBe emptyList()
    }

    test("plan folds the oldest turns once the tail would exceed the budget") {
        val messages = (1..5).map { turn("m$it", it.toLong()) }
        // Each turn costs 11 tokens ("x".repeat(40) + "mN" -> 42 chars -> ceil(42/4)=11).
        // Budget 25 fits exactly the last 2 turns (22 tokens) but not a 3rd (33 > 25).
        val plan = ChatCompactionPlanner.plan(messages, historyTokenBudget = 25)

        plan.verbatimTail shouldBe messages.takeLast(2)
        plan.turnsToFold shouldBe messages.dropLast(2)
    }

    test("plan always keeps the latest turn verbatim even if it alone exceeds the budget") {
        val messages = (1..3).map { turn("m$it", it.toLong()) }

        val plan = ChatCompactionPlanner.plan(messages, historyTokenBudget = 1)

        plan.verbatimTail shouldBe listOf(messages.last())
        plan.turnsToFold shouldBe messages.dropLast(1)
    }

    test("plan keeps at least as many turns verbatim with a larger budget") {
        val messages = (1..10).map { turn("m$it", it.toLong()) }

        val smallBudgetTail = ChatCompactionPlanner.plan(messages, historyTokenBudget = 20).verbatimTail
        val largeBudgetTail = ChatCompactionPlanner.plan(messages, historyTokenBudget = 200).verbatimTail

        (largeBudgetTail.size >= smallBudgetTail.size) shouldBe true
    }
})
