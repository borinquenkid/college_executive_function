package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Unit tests for [TermProfileAggregator] (ADR 0004 / ROADMAP Phase 13, XM-2).
 *
 * Fixtures are hand-authored, modeled on the real UT Austin fall 2025 / spring 2026 course
 * corpus under contributions/tx/ut_austin/2025-2026/ — not live-extracted via Gemini. Running
 * the full ingestion+extraction pipeline against those PDFs is what
 * [ContributorPdfIntegrationTest] already does (and costs a live API call); this aggregator only
 * needs representative [Event] shapes to exercise its own logic.
 */
class TermProfileAggregatorTest : FunSpec({

    fun deadline(sourceId: String, date: LocalDate, title: String = "Assignment") = DayEvent(
        id = "$sourceId-$title-$date",
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date,
        sourceId = sourceId
    )

    fun classEvent(sourceId: String, date: LocalDate) = DayEvent(
        id = "$sourceId-class-$date",
        title = "Class meeting",
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS,
        date = date,
        sourceId = sourceId
    )

    test("aggregate on an empty term returns null") {
        TermProfileAggregator.aggregate(emptyList()) shouldBe null
    }

    test("courseLoad counts distinct sourceId, not distinct titles or raw event count") {
        val events = listOf(
            deadline("M408N_differential_calculus.pdf", LocalDate(2025, 9, 3)),
            deadline("M408N_differential_calculus.pdf", LocalDate(2025, 10, 1)),
            classEvent("M408N_differential_calculus.pdf", LocalDate(2025, 9, 1)),
            deadline("BIO325_genetics.pdf", LocalDate(2025, 9, 15))
        )
        val profile = TermProfileAggregator.aggregate(events)!!
        profile.courseLoad shouldBe 2
    }

    test("categoryDistribution counts every category present") {
        val events = listOf(
            deadline("M408N_differential_calculus.pdf", LocalDate(2025, 9, 3)),
            classEvent("M408N_differential_calculus.pdf", LocalDate(2025, 9, 1)),
            classEvent("M408N_differential_calculus.pdf", LocalDate(2025, 9, 8))
        )
        val profile = TermProfileAggregator.aggregate(events)!!
        profile.categoryDistribution shouldBe mapOf(
            AcademicCategory.DEADLINE to 1,
            AcademicCategory.CLASS to 2
        )
    }

    test("deadlineCadenceByWeekday only counts DEADLINE-category events, keyed by day of week") {
        // 2025-09-03 is a Wednesday; 2025-09-08 is a Monday.
        val events = listOf(
            deadline("M408N_differential_calculus.pdf", LocalDate(2025, 9, 3)),
            deadline("BIO325_genetics.pdf", LocalDate(2025, 9, 8)),
            classEvent("M408N_differential_calculus.pdf", LocalDate(2025, 9, 3)) // not a DEADLINE — excluded
        )
        val profile = TermProfileAggregator.aggregate(events)!!
        profile.deadlineCadenceByWeekday shouldBe mapOf(
            DayOfWeek.WEDNESDAY to 1,
            DayOfWeek.MONDAY to 1
        )
    }

    test("termStart/termEnd is derived from the events' own max date, not a fixed 'today'") {
        val events = listOf(deadline("HIS378W_capstone.pdf", LocalDate(2025, 10, 20)))
        val profile = TermProfileAggregator.aggregate(events)!!
        profile.termStart shouldBe LocalDate(2025, 8, 1)
        profile.termEnd shouldBe LocalDate(2025, 12, 31)
    }

    test("BIO337-style same-course-number-different-subject case: aggregating fall and spring independently never merges them into one course") {
        // Mirrors the real ADR 0004 finding: BIO337 fall 2025 ("research methods") and spring
        // 2026 ("Science and Religion") share a course code but are unrelated subjects. Each
        // term is aggregated from its own event list — there is no cross-term merge step here
        // that could conflate the two, by construction (aggregate() only ever sees one term).
        val fallEvents = listOf(
            deadline("BIO337_research_methods.pdf", LocalDate(2025, 10, 6), "Research proposal"),
            classEvent("BIO337_research_methods.pdf", LocalDate(2025, 10, 1))
        )
        val springEvents = listOf(
            deadline("BIO337_science_and_religion.pdf", LocalDate(2026, 3, 4), "Reading response"),
            classEvent("BIO337_science_and_religion.pdf", LocalDate(2026, 3, 2)),
            classEvent("BIO337_science_and_religion.pdf", LocalDate(2026, 3, 9))
        )

        val fallProfile = TermProfileAggregator.aggregate(fallEvents)!!
        val springProfile = TermProfileAggregator.aggregate(springEvents)!!

        // Each profile counts BIO337 as exactly 1 course within its own term — independent counts,
        // not a shared "this course recurred" total.
        fallProfile.courseLoad shouldBe 1
        springProfile.courseLoad shouldBe 1
        fallProfile.categoryDistribution.getValue(AcademicCategory.CLASS) shouldBe 1
        springProfile.categoryDistribution.getValue(AcademicCategory.CLASS) shouldBe 2
        fallProfile.termStart shouldBe LocalDate(2025, 8, 1)
        springProfile.termStart shouldBe LocalDate(2026, 1, 1)
    }

    test("summarize on an empty profile list returns an empty string") {
        TermProfileAggregator.summarize(emptyList()) shouldBe ""
    }

    test("summarize renders average course load and top categories across multiple terms") {
        val fall = StudentTermProfile(
            termStart = LocalDate(2025, 8, 1), termEnd = LocalDate(2025, 12, 31),
            courseLoad = 4,
            categoryDistribution = mapOf(AcademicCategory.DEADLINE to 10, AcademicCategory.CLASS to 30),
            deadlineCadenceByWeekday = mapOf(DayOfWeek.WEDNESDAY to 6, DayOfWeek.FRIDAY to 4)
        )
        val spring = StudentTermProfile(
            termStart = LocalDate(2026, 1, 1), termEnd = LocalDate(2026, 5, 31),
            courseLoad = 6,
            categoryDistribution = mapOf(AcademicCategory.DEADLINE to 8, AcademicCategory.CLASS to 20),
            deadlineCadenceByWeekday = mapOf(DayOfWeek.WEDNESDAY to 5)
        )

        val summary = TermProfileAggregator.summarize(listOf(fall, spring))
        summary shouldBe "Across 2 prior terms, this student typically takes ~5 course(s) per term. " +
            "Most common event types: CLASS, DEADLINE. Deadlines cluster on: WEDNESDAY, FRIDAY."
    }
})
