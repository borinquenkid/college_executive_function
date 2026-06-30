package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.datetime.LocalDate

/**
 * Deterministic date-in-source detection. A DEADLINE/FINALS asserts a graded item is due on a
 * specific date; if that date is nowhere in the syllabus, the deliverable is ungrounded
 * (likely confabulated). This is a *classifier*, not a policy — what to do with the ungrounded
 * set (drop, warn, or send to a date-picker dialog) is decided downstream.
 *
 * The matcher must recognise the formats syllabi actually use, and must NOT match near-miss days.
 */
class SourceDateGrounderTest : FunSpec({

    val jul15 = LocalDate.parse("2026-07-15")

    test("matches the common syllabus date formats for July 15 2026") {
        val sources = listOf(
            "Final paper due 2026-07-15.",
            "Essay due July 15, 2026.",
            "essay due july 15 in class",
            "Quiz on Jul 15",
            "Reading for 15 July",
            "Submit by the 15th of July",
            "Midterm 7/15",
            "Midterm 07/15/2026",
            "Due 7-15-2026",
            "Tuesday, July 15: Final draft",
        )
        sources.forEach { src ->
            withClue(src) { SourceDateGrounder.isDateReferenced(jul15, src) shouldBe true }
        }
    }

    test("does not match a near-miss day") {
        SourceDateGrounder.isDateReferenced(LocalDate.parse("2026-07-16"), "Essay due July 15, 2026.") shouldBe false
        SourceDateGrounder.isDateReferenced(jul15, "There are july 150 things") shouldBe false
        SourceDateGrounder.isDateReferenced(jul15, "Read chapter 7, page 16.") shouldBe false
    }

    test("matches September abbreviated as sept") {
        SourceDateGrounder.isDateReferenced(LocalDate.parse("2026-09-03"), "First quiz: Sept 3.") shouldBe true
    }

    test("returns false when no date is present at all") {
        SourceDateGrounder.isDateReferenced(jul15, "This syllabus has no concrete dates.") shouldBe false
    }

    // ── classifyDeliverables ──────────────────────────────────────────────────

    fun day(category: AcademicCategory, date: String, title: String = category.name) =
        DayEvent(title = title, source = EventSource.AI_GENERATED, category = category, date = LocalDate.parse(date))

    test("classifies a DEADLINE with a source-referenced date as grounded, an unreferenced one as ungrounded") {
        val real = day(AcademicCategory.DEADLINE, "2026-07-15", "Essay")
        val fabricated = day(AcademicCategory.DEADLINE, "2026-08-20", "Phantom assignment")
        val c = SourceDateGrounder.classifyDeliverables(listOf(real, fabricated), "Essay due July 15, 2026.")
        c.grounded shouldBe listOf(real)
        c.ungrounded shouldBe listOf(fabricated)
    }

    test("treats STUDY_BLOCK and REGULAR as always grounded (their dates are not in the syllabus)") {
        val block = day(AcademicCategory.STUDY_BLOCK, "2026-07-10", "Study")
        val regular = day(AcademicCategory.REGULAR, "2026-07-09", "Writing center visit")
        val c = SourceDateGrounder.classifyDeliverables(listOf(block, regular), "Essay due July 15, 2026.")
        c.grounded.shouldContainExactlyInAnyOrder(block, regular)
        c.ungrounded shouldBe emptyList()
    }
})
