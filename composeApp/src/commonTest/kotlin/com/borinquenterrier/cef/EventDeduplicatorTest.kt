package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class EventDeduplicatorTest : FunSpec({

    val date1 = LocalDate(2026, 7, 14)
    val date2 = LocalDate(2026, 7, 16)
    val date3 = LocalDate(2026, 7, 30)

    fun dayEvent(title: String, date: LocalDate = date1, cat: AcademicCategory = AcademicCategory.DEADLINE) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, date = date, category = cat)

    fun timeEvent(title: String, date: LocalDate = date1) =
        TimeEvent(id = null, title = title, source = EventSource.AI_GENERATED,
            startTime = kotlinx.datetime.LocalTime(10, 0),
            endTime = kotlinx.datetime.LocalTime(11, 0),
            date = date, category = AcademicCategory.DEADLINE)

    // ── canonicalTitle ────────────────────────────────────────────────────────

    test("canonicalTitle lowercases and trims") {
        EventDeduplicator.canonicalTitle("  SUBMIT Essay  ") shouldBe "submit essay"
    }

    test("canonicalTitle strips leading 'your '") {
        EventDeduplicator.canonicalTitle("Your Essay") shouldBe "essay"
    }

    // ── submissionCanonical ───────────────────────────────────────────────────

    test("submissionCanonical strips leading submit verb") {
        EventDeduplicator.submissionCanonical("Submit Issue Brief #1") shouldBe "issue brief 1"
    }

    test("submissionCanonical strips leading complete verb") {
        EventDeduplicator.submissionCanonical("Complete the assignment") shouldBe "assignment"
    }

    test("submissionCanonical strips leading upload verb") {
        EventDeduplicator.submissionCanonical("Upload your essay") shouldBe "essay"
    }

    test("submissionCanonical strips leading post verb") {
        EventDeduplicator.submissionCanonical("Post discussion response") shouldBe "discussion response"
    }

    test("submissionCanonical strips hash from number references") {
        EventDeduplicator.submissionCanonical("Issue Brief #1") shouldBe "issue brief 1"
    }

    // ── commonPrefixLength ────────────────────────────────────────────────────

    test("commonPrefixLength returns 0 when strings differ at first char") {
        EventDeduplicator.commonPrefixLength("abc", "xyz") shouldBe 0
    }

    test("commonPrefixLength returns correct length for partial match") {
        EventDeduplicator.commonPrefixLength("issue brief #1 draft", "issue brief #1 final") shouldBe 15
    }

    test("commonPrefixLength returns length of shorter string when one is prefix") {
        EventDeduplicator.commonPrefixLength("abc", "abcdef") shouldBe 3
    }

    // ── dedupByCommonTitlePrefix ──────────────────────────────────────────────

    test("dedupByCommonTitlePrefix removes shorter title when 12+ char prefix shared on same date") {
        val short = dayEvent("Issue Brief #1 due")
        val long = dayEvent("Issue Brief #1: Connecting Hidden Systems")
        val result = EventDeduplicator.dedupByCommonTitlePrefix(listOf(short, long))
        result shouldHaveSize 1
        result[0].title shouldBe long.title
    }

    test("dedupByCommonTitlePrefix keeps both when prefix < 12 chars") {
        val a = dayEvent("Essay due")
        val b = dayEvent("Essay revision due")
        EventDeduplicator.dedupByCommonTitlePrefix(listOf(a, b)) shouldHaveSize 2
    }

    test("dedupByCommonTitlePrefix keeps both when on different dates") {
        val a = dayEvent("Issue Brief #1 draft", date1)
        val b = dayEvent("Issue Brief #1 final", date2)
        EventDeduplicator.dedupByCommonTitlePrefix(listOf(a, b)) shouldHaveSize 2
    }

    // ── dedupSubmissionPairs ──────────────────────────────────────────────────

    test("dedupSubmissionPairs removes earlier event when same assignment within 7 days") {
        val early = dayEvent("Issue Brief #1", date1)   // Jul 14
        val late  = dayEvent("Submit Issue Brief #1", date2)  // Jul 16
        val result = EventDeduplicator.dedupSubmissionPairs(listOf(early, late))
        result shouldHaveSize 1
        result[0].date shouldBe date2 // keeps the later date
    }

    test("dedupSubmissionPairs keeps both events more than 7 days apart") {
        val early = dayEvent("Issue Brief #1", date1)   // Jul 14
        val far   = dayEvent("Issue Brief #1 final", date3) // Jul 30
        val result = EventDeduplicator.dedupSubmissionPairs(listOf(early, far))
        result shouldHaveSize 2
    }

    test("dedupSubmissionPairs keeps distinct events with different assignments") {
        val a = dayEvent("Issue Brief #1 due", date1)
        val b = dayEvent("Issue Brief #2 due", date2)
        EventDeduplicator.dedupSubmissionPairs(listOf(a, b)) shouldHaveSize 2
    }

    // ── dedup (full pipeline) ─────────────────────────────────────────────────

    test("dedup prefers TimeEvent over DayEvent for same submission-canonical title and date") {
        val day = dayEvent("Submit Essay #1")
        val timed = timeEvent("Essay #1") // same canonical without "Submit"
        val result = EventDeduplicator.dedup(listOf(day, timed))
        result shouldHaveSize 1
        (result[0] is TimeEvent) shouldBe true
    }

    test("dedup returns empty list for empty input") {
        EventDeduplicator.dedup(emptyList()) shouldHaveSize 0
    }

    test("dedup passes through unique events unchanged") {
        val events = listOf(
            dayEvent("Essay #1", date1),
            dayEvent("Midterm", date2),
            dayEvent("Final Project", date3)
        )
        EventDeduplicator.dedup(events) shouldHaveSize 3
    }

    // ── dateOf ────────────────────────────────────────────────────────────────

    test("dateOf returns date for DayEvent") {
        EventDeduplicator.dateOf(dayEvent("Test")) shouldBe date1
    }

    test("dateOf returns date for TimeEvent") {
        EventDeduplicator.dateOf(timeEvent("Test")) shouldBe date1
    }
})
