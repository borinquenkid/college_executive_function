package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

class DecompositionOrchestratorTest : FunSpec({

    val rootTitle = "Research Paper"
    val rootDueDate = "2026-12-10"

    test("DecompositionOrchestrator should decompose flat tasks successfully") {
        val mockAi = mockk<AIService>()
        val orchestrator = DecompositionOrchestrator(mockAi, maxDepth = 3)

        val subtasks = listOf(
            DecomposedTask("Pick a topic", 5, "Choose a narrow topic"),
            DecomposedTask("Submit topic choice", 4, "Email the TA")
        )
        // Mock the root decomposition call
        coEvery { mockAi.decomposeTask(rootTitle, rootDueDate) } returns subtasks

        val result = runBlocking { orchestrator.decompose(rootTitle, rootDueDate) }

        // Pick a topic is 2 words, Submit topic choice is 3 words but daysBeforeDue is not > 1 and doesn't contain complex keywords.
        // So neither should be recursively decomposed.
        result shouldHaveSize 2
        result[0].title shouldBe "Pick a topic"
        result[0].daysBeforeDue shouldBe 5
        result[1].title shouldBe "Submit topic choice"
        result[1].daysBeforeDue shouldBe 4
    }

    test("DecompositionOrchestrator should recursively decompose complex sub-tasks") {
        val mockAi = mockk<AIService>()
        val orchestrator = DecompositionOrchestrator(mockAi, maxDepth = 3)

        // Root task: "Research Paper" (due 2026-12-10)
        // Generates:
        // 1. "Pick a topic" (due 2026-12-09, 1 day before due) -> Simple
        // 2. "Draft first essay section" (due 2026-12-05, 5 days before due) -> Complex
        val rootSubtasks = listOf(
            DecomposedTask("Pick a topic", 1, "Choose a narrow topic"),
            DecomposedTask("Draft first essay section", 5, "Write introduction and thesis")
        )

        // Decomposed "Draft first essay section" (due 2026-12-05)
        // Generates:
        // 1. "Write thesis statement" (due 2026-12-04, 1 day before parent due) -> Simple
        // 2. "Gather peer reviews" (due 2026-12-03, 2 days before parent due) -> Simple
        val draftSubtasks = listOf(
            DecomposedTask("Write thesis statement", 1, "Make it debatable"),
            DecomposedTask("Gather peer reviews", 2, "Ask 2 classmates")
        )

        coEvery { mockAi.decomposeTask(rootTitle, rootDueDate) } returns rootSubtasks
        coEvery {
            mockAi.decomposeTask(
                "Draft first essay section",
                "2026-12-05"
            )
        } returns draftSubtasks

        val result = runBlocking { orchestrator.decompose(rootTitle, rootDueDate) }

        // Final flat list should contain:
        // - "Pick a topic" -> scheduled 1 day before root (2026-12-09) -> daysBeforeDue = 1
        // - "Write thesis statement" -> scheduled 1 day before 2026-12-05 = 2026-12-04 -> daysBeforeDue = 6 (relative to root 2026-12-10)
        // - "Gather peer reviews" -> scheduled 2 days before 2026-12-05 = 2026-12-03 -> daysBeforeDue = 7 (relative to root 2026-12-10)
        result shouldHaveSize 3

        result[0].title shouldBe "Gather peer reviews"
        result[0].daysBeforeDue shouldBe 7

        result[1].title shouldBe "Write thesis statement"
        result[1].daysBeforeDue shouldBe 6

        result[2].title shouldBe "Pick a topic"
        result[2].daysBeforeDue shouldBe 1
    }

    test("DecompositionOrchestrator re-throws QuotaExhausted so EventAgent can surface it") {
        val mockAi = mockk<AIService>()
        val orchestrator = DecompositionOrchestrator(mockAi, maxDepth = 3)
        coEvery { mockAi.decomposeTask(any(), any()) } throws RuntimeException("QuotaExhausted: 429")

        val ex = runCatching { runBlocking { orchestrator.decompose(rootTitle, rootDueDate) } }
        ex.isFailure shouldBe true
        ex.exceptionOrNull()?.message?.contains("QuotaExhausted") shouldBe true
    }

    test("DecompositionOrchestrator re-throws RateLimited errors") {
        val mockAi = mockk<AIService>()
        val orchestrator = DecompositionOrchestrator(mockAi, maxDepth = 3)
        coEvery { mockAi.decomposeTask(any(), any()) } throws RuntimeException("RateLimited: retry in 60s")

        val ex = runCatching { runBlocking { orchestrator.decompose(rootTitle, rootDueDate) } }
        ex.isFailure shouldBe true
    }

    test("DecompositionOrchestrator falls back silently for generic errors") {
        val mockAi = mockk<AIService>()
        val orchestrator = DecompositionOrchestrator(mockAi, maxDepth = 3)
        coEvery { mockAi.decomposeTask(rootTitle, rootDueDate) } throws RuntimeException("network timeout")

        val result = runBlocking { orchestrator.decompose(rootTitle, rootDueDate) }
        result shouldHaveSize 1
        result[0].title shouldBe rootTitle
        result[0].description.shouldContain("network timeout")
    }

    test("DecompositionOrchestrator should stop at max depth limit") {
        val mockAi = mockk<AIService>()
        // Set maxDepth to 1 to verify it stops immediately at depth 1
        val orchestrator = DecompositionOrchestrator(mockAi, maxDepth = 1)

        val rootSubtasks = listOf(
            DecomposedTask("Draft first essay section", 5, "Write introduction and thesis")
        )
        coEvery { mockAi.decomposeTask(rootTitle, rootDueDate) } returns rootSubtasks

        val result = runBlocking { orchestrator.decompose(rootTitle, rootDueDate) }

        // Since maxDepth = 1, "Draft first essay section" is not decomposed further and is kept as leaf
        result shouldHaveSize 1
        result[0].title shouldBe "Draft first essay section"
        result[0].daysBeforeDue shouldBe 5
    }
})
