package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Synthetic scenario tests for [DecompositionOrchestrator].
 *
 * Uses mock AI (no real API calls) to exercise the four time-window scenarios
 * a student might face. Dates are always computed relative to today so the
 * scenarios remain meaningful no matter when the test suite is run.
 *
 * Scenarios:
 *   COMFORTABLE  – 30 days, major paper   → 5–9 steps, none > 30d before
 *   NORMAL       – 14 days, short essay   → 5–9 steps, none > 14d before
 *   TIGHT        – 3 days, short essay    → steps capped at 3d before
 *   CRISIS       – due today              → steps capped at 0d before (emergency mode)
 */
class DecompositionScenarioTest : FunSpec({

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // Helpers to build synthetic mock responses for each scenario
    fun steps(vararg pairs: Pair<String, Int>) = pairs.map { (title, days) ->
        DecomposedTask(title = title, daysBeforeDue = days, description = "Do $title")
    }

    // ── COMFORTABLE: 30 days, major paper ─────────────────────────────────────
    test("COMFORTABLE 30d — produces 5-9 steps all within 30 days before due") {
        val dueDate = today.plus(30, DateTimeUnit.DAY).toString()
        val mockAi = mockk<AIService>()
        coEvery { mockAi.decomposeTask(any(), any()) } returns steps(
            "Review assignment prompt" to 29,
            "Research key sources (3 articles)" to 25,
            "Draft annotated bibliography" to 22,
            "Write detailed outline" to 18,
            "Draft introduction and body" to 12,
            "Revise first draft" to 7,
            "Peer review and final edits" to 3,
            "Proofread and submit" to 0
        )

        val result = DecompositionOrchestrator(mockAi, maxDepth = 2).decompose(
            "Final Paper: Hidden Systems in American Justice", dueDate
        )

        result.size shouldBeGreaterThanOrEqual 5
        result.size shouldBeLessThanOrEqual 9
        result.forEach { task ->
            task.title.shouldNotBeBlank()
            task.daysBeforeDue shouldBeLessThanOrEqual 30
            task.daysBeforeDue shouldBeGreaterThanOrEqual 0
        }
    }

    // ── NORMAL: 14 days, short essay ──────────────────────────────────────────
    test("NORMAL 14d — produces 5-9 steps all within 14 days before due") {
        val dueDate = today.plus(14, DateTimeUnit.DAY).toString()
        val mockAi = mockk<AIService>()
        coEvery { mockAi.decomposeTask(any(), any()) } returns steps(
            "Read assignment prompt and rubric" to 13,
            "Brainstorm and choose argument" to 11,
            "Research 2-3 supporting sources" to 9,
            "Write outline" to 7,
            "Draft essay body" to 5,
            "Revise and edit" to 2,
            "Final proofread and submit" to 0
        )

        val result = DecompositionOrchestrator(mockAi, maxDepth = 2).decompose(
            "Issue Brief #1: Secrecy, Identity", dueDate
        )

        result.size shouldBeGreaterThanOrEqual 5
        result.size shouldBeLessThanOrEqual 9
        result.forEach { task ->
            task.title.shouldNotBeBlank()
            task.daysBeforeDue shouldBeLessThanOrEqual 14
            task.daysBeforeDue shouldBeGreaterThanOrEqual 0
        }
        // Steps should be ordered most-days-before-due first (orchestrator sorts descending)
        val days = result.map { it.daysBeforeDue }
        days shouldBe days.sortedDescending()
    }

    // ── TIGHT: 3 days, short essay ────────────────────────────────────────────
    test("TIGHT 3d — all steps fall within 3 days before due") {
        val dueDate = today.plus(3, DateTimeUnit.DAY).toString()
        val mockAi = mockk<AIService>()
        coEvery { mockAi.decomposeTask(any(), any()) } returns steps(
            "Skim prompt and pick argument" to 3,
            "Draft with sources already in syllabus" to 2,
            "Quick revision pass" to 1,
            "Proofread and submit" to 0
        )

        val result = DecompositionOrchestrator(mockAi, maxDepth = 2).decompose(
            "Issue Brief #1: Secrecy, Identity", dueDate
        )

        result.size shouldBeGreaterThanOrEqual 2
        result.size shouldBeLessThanOrEqual 7
        result.forEach { task ->
            task.title.shouldNotBeBlank()
            // No step should be scheduled before today (daysBeforeDue > 3 would mean before today)
            task.daysBeforeDue shouldBeLessThanOrEqual 3
            task.daysBeforeDue shouldBeGreaterThanOrEqual 0
        }
    }

    // ── CRISIS: due today ─────────────────────────────────────────────────────
    test("CRISIS due-today — steps reference emergency triage and partial credit") {
        val dueDate = today.toString()
        val mockAi = mockk<AIService>()
        coEvery { mockAi.decomposeTask(any(), any()) } returns steps(
            "Email professor — request 24h extension or clarify late policy" to 0,
            "Write 1-paragraph response addressing the core question" to 0,
            "Submit what you have with a note on your approach" to 0
        )

        val result = DecompositionOrchestrator(mockAi, maxDepth = 2).decompose(
            "Issue Brief #1: Secrecy, Identity", dueDate
        )

        result.size shouldBeGreaterThanOrEqual 1
        result.size shouldBeLessThanOrEqual 4
        result.forEach { task ->
            task.title.shouldNotBeBlank()
            task.daysBeforeDue shouldBe 0
        }
        // At least one step should reference extension, late policy, or partial credit
        val allText = result.joinToString(" ") { it.title + " " + it.description }.lowercase()
        val hasTriage = listOf("extension", "late", "partial", "professor", "submit").any { allText.contains(it) }
        hasTriage shouldBe true
    }

    // ── ORCHESTRATOR: does not recurse on tight-window steps ──────────────────
    test("orchestrator does not recurse when mock returns steps with daysBeforeDue <= 3") {
        val dueDate = today.plus(5, DateTimeUnit.DAY).toString()
        val mockAi = mockk<AIService>()
        var callCount = 0
        coEvery { mockAi.decomposeTask(any(), any()) } answers {
            callCount++
            // Return steps all <= 3 days before — none should trigger recursion
            steps(
                "Draft intro paragraph" to 3,
                "Draft body" to 2,
                "Submit" to 0
            )
        }

        DecompositionOrchestrator(mockAi, maxDepth = 3).decompose(
            "Issue Brief #1: Secrecy, Identity", dueDate
        )

        // Only the root call should fire — no recursion into the short-window subtasks
        callCount shouldBe 1
    }
})
