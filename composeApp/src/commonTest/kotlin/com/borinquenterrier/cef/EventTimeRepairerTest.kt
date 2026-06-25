package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventTimeRepairerTest : FunSpec({

    val date = LocalDate(2026, 9, 1)

    fun time(h: Int, m: Int = 0) = LocalTime(h, m)

    fun event(start: LocalTime, end: LocalTime) = TimeEvent(
        title = "E",
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.STUDY_BLOCK,
        date = date,
        startTime = start,
        endTime = end
    )

    test("returns event unchanged when endTime is after startTime") {
        val e = event(time(9), time(10))
        EventTimeRepairer.repair(e) shouldBeSameInstanceAs e
    }

    test("repairs endTime to startTime + 1h when times are equal") {
        val e = event(time(9), time(9))
        val repaired = EventTimeRepairer.repair(e) as TimeEvent
        repaired.endTime shouldBe time(10)
    }

    test("sets endTime to startTime + 1 hour when inverted") {
        val e = event(time(10), time(9))
        val repaired = EventTimeRepairer.repair(e) as TimeEvent
        repaired.startTime shouldBe time(10)
        repaired.endTime shouldBe time(11)
    }

    test("converts to DayEvent when startTime is at 23:30 and adding 1h overflows midnight") {
        val e = event(time(23, 30), time(22, 0))
        val repaired = EventTimeRepairer.repair(e)
        repaired shouldBe DayEvent(
            title = "E",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date
        )
    }

    test("converts to DayEvent when startTime is exactly 23:59 and adding 1h overflows midnight") {
        val e = event(time(23, 59), time(23, 0))
        val repaired = EventTimeRepairer.repair(e)
        (repaired is DayEvent) shouldBe true
        repaired.date shouldBe date
        repaired.title shouldBe "E"
    }

    test("preserves gradeWeight when converting to DayEvent on overflow") {
        val e = TimeEvent(
            title = "Final Exam",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.FINALS,
            date = date,
            startTime = time(23, 0),
            endTime = time(22, 0),
            gradeWeight = 0.4f
        )
        val repaired = EventTimeRepairer.repair(e) as DayEvent
        repaired.gradeWeight shouldBe 0.4f
    }

    test("does not convert to DayEvent when startTime is 22:30 (no overflow)") {
        val e = event(time(22, 30), time(21, 0))
        val repaired = EventTimeRepairer.repair(e) as TimeEvent
        repaired.endTime shouldBe time(23, 30)
    }

    test("returns DayEvent unchanged (no time to repair)") {
        val day = DayEvent(
            title = "E",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = date
        )
        EventTimeRepairer.repair(day) shouldBeSameInstanceAs day
    }
})
