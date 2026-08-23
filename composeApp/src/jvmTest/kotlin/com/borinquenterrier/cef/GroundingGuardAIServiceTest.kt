package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

/**
 * GroundingGuardAIService is the single, outermost place the "does this event have any
 * grounding in the source text" check lives — wrapping the entire delegate chain
 * (CriticActorAIService, its critique loops, RecursiveDecompositionAIService, RealAIService)
 * so that no matter how many internal generation or "correction" passes happen, exactly one
 * deterministic, non-AI fact-check runs on whatever finally comes out.
 *
 * These tests mock the delegate directly — the guard doesn't care whether the confabulated
 * event came from the first pass, a critique "correction", or anywhere else inside the chain;
 * it only cares whether the final answer is grounded in the input it was given.
 */
class GroundingGuardAIServiceTest : FunSpec({

    lateinit var delegate: AIService
    lateinit var guard: GroundingGuardAIService

    beforeEach {
        delegate = mockk(relaxed = true)
        guard = GroundingGuardAIService(delegate, mockk(relaxed = true))
    }

    fun dayEvent(year: Int, month: Int = 1, day: Int = 1, title: String = "Event $year") = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.REGULAR,
        date = LocalDate(year, month, day)
    )

    // --- Year-level grounding ---
    //
    // generateCalendarEvents itself does NOT year-ground (moved to EventGenerationService,
    // see EventGenerationServiceTest's "document-level year grounding" section) — this method
    // only ever sees whatever single batch of fragments its caller passed in, which for a
    // multi-batch source is not the whole document. Grounding per-batch here false-dropped real
    // events whenever a batch's own text happened to lack the document's year while another
    // batch's did (confirmed live 2026-08-23: a batch containing an advising-office phone number
    // "636-422-2000" but not the source's "Fall 2026" header wrongly zeroed out 7 real events).

    test("generateCalendarEvents passes events through unfiltered regardless of year") {
        val fragments = listOf(SourceFragment("Spring 2026 reading list. No other dates mentioned."))
        coEvery { delegate.generateCalendarEvents(any()) } returns listOf(
            dayEvent(2026, title = "Reading Response"),
            dayEvent(2099, title = "Class Meeting") // would have been dropped by the old per-batch check
        )

        val result = guard.generateCalendarEvents(fragments)

        result.shouldHaveSize(2)
    }

    test("generateStudyPlan drops events whose year never appears in the source — closes the gap that produced academic_calendar.ics") {
        val syllabusText =
            "PRINTED BY: Acme Publishing. No part of this book may be reproduced. DO NOT COPY."
        coEvery { delegate.generateStudyPlan(any(), any(), any()) } returns listOf(
            dayEvent(2026, 9, 1, "Class Meeting"),
            dayEvent(2026, 9, 3, "Class Meeting"),
            dayEvent(2026, 10, 14, "Midterm Exam")
        )

        // Source has no year → filterToSourceYears no-ops → all events pass
        val result = guard.generateStudyPlan(syllabusText)
        result.shouldHaveSize(3)

        // Guard bites once the source gives it something to check against
        coEvery { delegate.generateStudyPlan(any(), any(), any()) } returns listOf(
            dayEvent(2024, 10, 14, "Midterm Exam"),  // grounded — 2024 is in the source
            dayEvent(2099, 9, 1,  "Class Meeting")   // confabulated — 2099 nowhere in source
        )
        val grounded = guard.generateStudyPlan("Course schedule for Fall 2024.")
        grounded.shouldHaveSize(1)
        grounded[0].title shouldBe "Midterm Exam"
    }

    test("passes through other AIService methods unchanged") {
        coEvery { delegate.generateChatResponse(any()) } returns "chat response"
        guard.generateChatResponse("hi") shouldBe "chat response"
        coVerify(exactly = 1) { delegate.generateChatResponse("hi") }
    }

    // --- Source-fact grounding gate (level 2) ---

    test("generateChatResponse passes through cleanly when all dates in response are in the prompt") {
        val prompt = "Source: Midterm on October 14, worth 25%.\nUser: when is my midterm?"
        coEvery { delegate.generateChatResponse(any()) } returns "Your midterm is October 14 and counts for 25%."

        val result = guard.generateChatResponse(prompt)

        result shouldBe "Your midterm is October 14 and counts for 25%."
    }

    test("generateChatResponse appends warning when LLM invents a date not in the prompt") {
        val prompt = "Source: Midterm on October 14.\nUser: when is my essay due?"
        coEvery { delegate.generateChatResponse(any()) } returns "Your essay is due November 28."

        val result = guard.generateChatResponse(prompt)

        result.contains("November 28") shouldBe true
        result.contains("could not be verified") shouldBe true
    }

    test("generateChatResponse passes through when response has no extractable claims") {
        val prompt = "User: how should I study for this course?"
        coEvery { delegate.generateChatResponse(any()) } returns "Start by reviewing lecture notes each week."

        val result = guard.generateChatResponse(prompt)

        result shouldBe "Start by reviewing lecture notes each week."
    }

    test("analyzeDocument passes through unchanged — gate not applied to JSON metadata output") {
        val json = """{"grading_scale": "Midterm 25%, Final 40%", "late_policy": "10% per day"}"""
        coEvery { delegate.analyzeDocument(any()) } returns json

        val result = guard.analyzeDocument("Some syllabus text.")

        result shouldBe json
    }

    test("analyzeDocument returns null when delegate returns null") {
        coEvery { delegate.analyzeDocument(any()) } returns null

        val result = guard.analyzeDocument("Some syllabus text.")

        result shouldBe null
    }

    // --- Decomposition grounding (ADR 0014) ---

    test("decomposeTask passes through unchanged when no source context was resolved") {
        val tasks = listOf(DecomposedTask("Draft outline", 5, "Sketch the main argument"))
        coEvery { delegate.decomposeTask("Essay", "2026-12-01", "") } returns tasks

        val result = guard.decomposeTask("Essay", "2026-12-01", "")

        result shouldBe tasks
    }

    test("decomposeTask flags a sub-task description asserting a date not in the source") {
        val source = "Essay due Dec 1. Worth 20% of the final grade."
        coEvery { delegate.decomposeTask("Essay", "2026-12-01", source) } returns listOf(
            DecomposedTask("Draft outline", 5, "Outline due November 28, worth 20%")
        )

        val result = guard.decomposeTask("Essay", "2026-12-01", source)

        result shouldHaveSize 1
        result[0].description.contains("November 28") shouldBe true
        result[0].description.contains("could not be verified") shouldBe true
    }

    test("decomposeTask leaves a sub-task unflagged when its claims are backed by the source") {
        val source = "Essay due Dec 1. Worth 20% of the final grade."
        coEvery { delegate.decomposeTask("Essay", "2026-12-01", source) } returns listOf(
            DecomposedTask("Final review", 0, "Submit before Dec 1, worth 20% of your grade")
        )

        val result = guard.decomposeTask("Essay", "2026-12-01", source)

        result[0].description shouldBe "Submit before Dec 1, worth 20% of your grade"
    }
})
