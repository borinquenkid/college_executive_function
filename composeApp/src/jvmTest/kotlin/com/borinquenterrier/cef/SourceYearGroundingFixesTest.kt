package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate

/**
 * Regression tests for the Missouri S&T Fall 2026 findings (see MissouriSt2026SyllabusFixtureTest):
 * a textbook edition year must not be mistaken for the term year, a year-less source must not keep
 * stray-year guesses, a date-free document must yield no events, and the syllabus auditor must
 * survive the grounder's appended "[Note: ...]" line.
 */
class SourceYearGroundingFixesTest : FunSpec({

    fun day(title: String, y: Int, m: Int, d: Int, category: AcademicCategory = AcademicCategory.CLASS) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = category, date = LocalDate(y, m, d))

    context("extractSourceYears ignores citation years") {
        test("edition year in parentheses is not a term year") {
            GeminiResponseParser.extractSourceYears("""Texts: Public Speaking: 4th Edition" (2019). Fall semester.""") shouldBe emptySet()
        }
        test("copyright year is ignored") {
            GeminiResponseParser.extractSourceYears("Text © 2018 Pearson. Classes begin Aug 24.") shouldBe emptySet()
        }
        test("a real term year still counts alongside an edition year") {
            GeminiResponseParser.extractSourceYears("Fall Semester 2026. Text: Stats, 2nd Edition (2019)") shouldBe setOf(2026)
        }
        test("a parenthesised year that is not after 'Edition' still counts") {
            GeminiResponseParser.extractSourceYears("Fall Semester (2026)") shouldBe setOf(2026)
        }
    }

    context("keepDominantYearWhenUngrounded") {
        val events = listOf(day("A", 2026, 9, 1), day("B", 2026, 9, 3), day("C", 2026, 9, 8), day("stray", 2024, 1, 1))

        test("drops the minority year when the source states no year") {
            GeminiResponseParser.keepDominantYearWhenUngrounded(events, emptySet()).map { it.title } shouldBe listOf("A", "B", "C")
        }
        test("no-op when the source has grounded years (filterToSourceYears owns that case)") {
            GeminiResponseParser.keepDominantYearWhenUngrounded(events, setOf(2026)) shouldBe events
        }
        test("no-op when all events share one year") {
            val one = events.take(3)
            GeminiResponseParser.keepDominantYearWhenUngrounded(one, emptySet()) shouldBe one
        }
    }

    context("SourceDateEvidence") {
        test("plain assignment prose has no datable content") {
            SourceDateEvidence.hasDatableContent("Phase 1: Conceptual design (20%) - 60 pts. Deliver a proposal.") shouldBe false
        }
        test("recognises numeric, month-name and day-month dates") {
            SourceDateEvidence.hasDatableContent("Due 9/15") shouldBe true
            SourceDateEvidence.hasDatableContent("Exam on October 13") shouldBe true
            SourceDateEvidence.hasDatableContent("Wk 1 - Aug 24 - 28") shouldBe true
            SourceDateEvidence.hasDatableContent("Sept. 28–Oct. 2") shouldBe true
            SourceDateEvidence.hasDatableContent("due 15 December") shouldBe true
            SourceDateEvidence.hasDatableContent("released 2026-09-03") shouldBe true
        }
        test("a bare month name in prose is not a date") {
            SourceDateEvidence.hasDatableContent("The March of progress and a May day exercise") shouldBe false
        }
    }

    context("SyllabusAuditor tolerates the grounder's appended note") {
        val json = """{"hasAmbiguities": true, "findings": [{"type": "DATE_CONTRADICTION", "description": "Nov 21 is a Saturday", "severity": "MEDIUM"}]}"""
        val noted = "$json\n\n[Note: the following specific claims could not be verified in your loaded syllabi: November 21. Please confirm these directly with your course materials.]"

        test("audit still returns the findings") {
            val ai = mockk<AIService>()
            coEvery { ai.generateChatResponse(any()) } returns noted
            SyllabusAuditor(ai).audit("text") shouldBe listOf("[DATE_CONTRADICTION] Nov 21 is a Saturday")
        }
        test("fenced JSON still works") {
            val ai = mockk<AIService>()
            coEvery { ai.generateChatResponse(any()) } returns "```json\n$json\n```"
            SyllabusAuditor(ai).audit("text") shouldBe listOf("[DATE_CONTRADICTION] Nov 21 is a Saturday")
        }
        test("garbage yields no warnings") {
            val ai = mockk<AIService>()
            coEvery { ai.generateChatResponse(any()) } returns "not json"
            SyllabusAuditor(ai).audit("text").shouldBeEmpty()
        }
    }
})
