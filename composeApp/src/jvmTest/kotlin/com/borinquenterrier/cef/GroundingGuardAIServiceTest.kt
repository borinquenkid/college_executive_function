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

    test("generateCalendarEvents drops events whose year never appears in the source, no matter how the delegate produced them") {
        val fragments = listOf(SourceFragment("Spring 2026 reading list. No other dates mentioned."))
        coEvery { delegate.generateCalendarEvents(any()) } returns listOf(
            dayEvent(2026, title = "Reading Response"),
            dayEvent(2099, title = "Class Meeting") // confabulated — 2099 nowhere in source
        )

        val result = guard.generateCalendarEvents(fragments)

        result.shouldHaveSize(1)
        result[0].title shouldBe "Reading Response"
    }

    test("generateCalendarEvents keeps events whose year is grounded in the source") {
        val fragments = listOf(SourceFragment("Course runs Fall 2025 through Spring 2026."))
        coEvery { delegate.generateCalendarEvents(any()) } returns listOf(
            dayEvent(2025, title = "Kickoff"),
            dayEvent(2026, title = "Finals")
        )

        guard.generateCalendarEvents(fragments).shouldHaveSize(2)
    }

    test("generateCalendarEvents keeps Fall events from a Fall syllabus loaded in Summer") {
        // A student loading their Fall 2026 syllabus in June should get all Fall events.
        // The guard must not filter by today's date — only by years mentioned in the source.
        val fragments = listOf(SourceFragment("ENG 301 Fall 2026 — Course Syllabus."))
        coEvery { delegate.generateCalendarEvents(any()) } returns listOf(
            dayEvent(2026, 10, 14, "Midterm Exam"),
            dayEvent(2026, 12, 10, "Final Exam"),
            dayEvent(2026, 11, 3,  "Essay Due")
        )

        guard.generateCalendarEvents(fragments).shouldHaveSize(3)
    }

    test("generateCalendarEvents keeps all semesters from a multi-semester academic calendar") {
        // Loading a full-year calendar should return events from all semesters it covers —
        // the student may be planning ahead for Spring and Fall while currently in Summer.
        val fragments = listOf(SourceFragment(
            "Spring 2026 (Jan–May). Summer 2026 (May–Aug). Fall 2026 (Aug–Dec)."
        ))
        coEvery { delegate.generateCalendarEvents(any()) } returns listOf(
            dayEvent(2026, 3, 15, "Spring Break"),
            dayEvent(2026, 7, 2,  "Summer Session Ends"),
            dayEvent(2026, 10, 12, "Fall Midterms"),
            dayEvent(2026, 12, 14, "Fall Finals")
        )

        guard.generateCalendarEvents(fragments).shouldHaveSize(4)
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
})
