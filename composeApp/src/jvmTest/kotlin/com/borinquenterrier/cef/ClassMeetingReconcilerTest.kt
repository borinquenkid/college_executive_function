package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import kotlinx.datetime.LocalDate

class ClassMeetingReconcilerTest : FunSpec({

    fun classOn(date: LocalDate) = DayEvent(
        title = "Class session",
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS,
        date = date
    )

    fun deadlineOn(date: LocalDate, title: String = "Assignment due") = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date
    )

    // The 2026 STLCC ENG 101 grid: Mon+Wed, Jun 8 – Jul 29 (8 weeks, 16 dates).
    val mondays = (0..7).map { LocalDate(2026, 6, 8).plusWeeks(it) }
    val wednesdays = (0..7).map { LocalDate(2026, 6, 10).plusWeeks(it) }

    test("fills the one class date the model missed (2026-08-09 eval failure replica)") {
        // Jul 1 (Wed, week 4) had deadlines extracted but no CLASS event — the exact miss
        // that dropped the eval to 15/16.
        val jul1 = LocalDate(2026, 7, 1)
        val extracted = (mondays + wednesdays - jul1).map { classOn(it) } +
            deadlineOn(jul1, "Issue Brief #1 due") +
            deadlineOn(jul1, "Post to the Undercover Work Discussion")

        val result = ClassMeetingReconciler.fillMissedMeetings(extracted)

        val added = result - extracted.toSet()
        added.size shouldBe 1
        val synthesized = added.single()
        synthesized.date shouldBe jul1
        synthesized.category shouldBe AcademicCategory.CLASS
        (synthesized is DayEvent) shouldBe true
        synthesized.warning shouldNotBe null
        synthesized.warning!! stringShouldContain "Wednesday"
        synthesized.warning!! stringShouldContain "double-check"
    }

    test("does nothing when the extraction is already complete") {
        val extracted = (mondays + wednesdays).map { classOn(it) }

        ClassMeetingReconciler.fillMissedMeetings(extracted) shouldBe extracted
    }

    test("is idempotent — a second pass adds nothing") {
        val jul1 = LocalDate(2026, 7, 1)
        val extracted = (mondays + wednesdays - jul1).map { classOn(it) }

        val once = ClassMeetingReconciler.fillMissedMeetings(extracted)
        val twice = ClassMeetingReconciler.fillMissedMeetings(once)

        once.size shouldBe extracted.size + 1
        twice shouldBe once
    }

    test("does nothing with fewer than 6 distinct class dates") {
        val extracted = (mondays.take(3) + wednesdays.take(2)).map { classOn(it) }

        ClassMeetingReconciler.fillMissedMeetings(extracted) shouldBe extracted
    }

    test("does nothing when no weekday recurs often enough to form a pattern") {
        // Six one-off sessions on six different weekdays — nothing recurs.
        val extracted = listOf(
            LocalDate(2026, 6, 8),   // Mon
            LocalDate(2026, 6, 16),  // Tue
            LocalDate(2026, 6, 24),  // Wed
            LocalDate(2026, 7, 2),   // Thu
            LocalDate(2026, 7, 10),  // Fri
            LocalDate(2026, 7, 18)   // Sat
        ).map { classOn(it) }

        ClassMeetingReconciler.fillMissedMeetings(extracted) shouldBe extracted
    }

    test("never fills a date carrying holiday or no-class evidence") {
        val jul1 = LocalDate(2026, 7, 1)
        val jun24 = LocalDate(2026, 6, 24)
        val holidayGap = (mondays + wednesdays - jul1 - jun24).map { classOn(it) } +
            DayEvent(
                title = "Independence Day observed",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.HOLIDAY,
                date = jul1
            ) +
            DayEvent(
                title = "No class — instructor conference",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.REGULAR,
                date = jun24
            )

        val result = ClassMeetingReconciler.fillMissedMeetings(holidayGap)

        result shouldBe holidayGap
    }

    test("refuses to fill 3 or more gaps — that is a schedule change, not a model miss") {
        val missing = setOf(
            LocalDate(2026, 6, 24),
            LocalDate(2026, 7, 1),
            LocalDate(2026, 7, 8)
        )
        val extracted = (mondays + wednesdays).filterNot { it in missing }.map { classOn(it) }

        ClassMeetingReconciler.fillMissedMeetings(extracted) shouldBe extracted
    }

    test("never extends the grid beyond the observed first and last class dates") {
        // Wed-only seminar, 7 of 8 dates present with the 3rd missing: fills only that one,
        // nothing before Jun 10 or after Jul 29.
        val jun24 = LocalDate(2026, 6, 24)
        val extracted = (wednesdays - jun24).map { classOn(it) }

        val result = ClassMeetingReconciler.fillMissedMeetings(extracted)

        val added = result - extracted.toSet()
        added.map { it.date } shouldBe listOf(jun24)
    }

    test("other-category events on the missing date do not block the fill") {
        val jul1 = LocalDate(2026, 7, 1)
        val extracted = (mondays + wednesdays - jul1).map { classOn(it) } + deadlineOn(jul1)

        val result = ClassMeetingReconciler.fillMissedMeetings(extracted)

        result.filter { it.category == AcademicCategory.CLASS }.map { it.date } shouldContain jul1
    }
})

private fun LocalDate.plusWeeks(weeks: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() + weeks * 7)
