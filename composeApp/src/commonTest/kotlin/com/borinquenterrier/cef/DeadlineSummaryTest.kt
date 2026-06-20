package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class DeadlineSummaryTest : FunSpec({

    val today = LocalDate(2026, 9, 1)

    fun deadline(date: LocalDate) = DayEvent(
        title = "Essay", source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, date = date
    )

    fun finals(date: LocalDate) = DayEvent(
        title = "Final", source = EventSource.AI_GENERATED,
        category = AcademicCategory.FINALS, date = date
    )

    fun classEvent(date: LocalDate) = DayEvent(
        title = "Lecture", source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS, date = date
    )

    test("empty list produces zero counts") {
        val s = DeadlineSummary.from(emptyList(), today)
        s.dueIn7Days shouldBe 0
        s.dueIn30Days shouldBe 0
    }

    test("CLASS events are not counted") {
        val s = DeadlineSummary.from(listOf(classEvent(today.plus(1, DateTimeUnit.DAY))), today)
        s.dueIn7Days shouldBe 0
        s.dueIn30Days shouldBe 0
    }

    test("deadline on today counts in both windows") {
        val s = DeadlineSummary.from(listOf(deadline(today)), today)
        s.dueIn7Days shouldBe 1
        s.dueIn30Days shouldBe 1
    }

    test("deadline on day 7 counts in both windows") {
        val s = DeadlineSummary.from(listOf(deadline(today.plus(7, DateTimeUnit.DAY))), today)
        s.dueIn7Days shouldBe 1
        s.dueIn30Days shouldBe 1
    }

    test("deadline on day 8 counts only in 30-day window") {
        val s = DeadlineSummary.from(listOf(deadline(today.plus(8, DateTimeUnit.DAY))), today)
        s.dueIn7Days shouldBe 0
        s.dueIn30Days shouldBe 1
    }

    test("deadline on day 30 counts in 30-day window") {
        val s = DeadlineSummary.from(listOf(deadline(today.plus(30, DateTimeUnit.DAY))), today)
        s.dueIn7Days shouldBe 0
        s.dueIn30Days shouldBe 1
    }

    test("deadline on day 31 is outside both windows") {
        val s = DeadlineSummary.from(listOf(deadline(today.plus(31, DateTimeUnit.DAY))), today)
        s.dueIn7Days shouldBe 0
        s.dueIn30Days shouldBe 0
    }

    test("FINALS category counts the same as DEADLINE") {
        val s = DeadlineSummary.from(listOf(finals(today.plus(3, DateTimeUnit.DAY))), today)
        s.dueIn7Days shouldBe 1
        s.dueIn30Days shouldBe 1
    }

    test("multiple events are all counted correctly") {
        val events = listOf(
            deadline(today.plus(2, DateTimeUnit.DAY)),   // 7-day + 30-day
            deadline(today.plus(10, DateTimeUnit.DAY)),  // 30-day only
            deadline(today.plus(35, DateTimeUnit.DAY)),  // neither
            finals(today.plus(5, DateTimeUnit.DAY)),     // 7-day + 30-day
            classEvent(today.plus(1, DateTimeUnit.DAY))  // ignored
        )
        val s = DeadlineSummary.from(events, today)
        s.dueIn7Days shouldBe 2
        s.dueIn30Days shouldBe 3
    }
})
