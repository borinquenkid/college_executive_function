package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.days

class EventProgressTest : FunSpec({
    test("timeUntilDue should return positive days for future events") {
        val event = DayEvent(
            title = "Future Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10)
        )
        val today = LocalDate(2026, 6, 2)
        event.timeUntilDue(today) shouldBe 8.days
    }

    test("timeUntilDue should return zero for events due today") {
        val event = DayEvent(
            title = "Today's Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 2)
        )
        val today = LocalDate(2026, 6, 2)
        event.timeUntilDue(today) shouldBe 0.days
    }

    test("timeUntilDue should return negative days for past events") {
        val event = DayEvent(
            title = "Past Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 5, 25)
        )
        val today = LocalDate(2026, 6, 2)
        event.timeUntilDue(today) shouldBe (-8).days
    }

    test("studyProgress should return 0f for non-academic categories") {
        val event = DayEvent(
            title = "Regular Event",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2026, 6, 10)
        )
        val today = LocalDate(2026, 6, 5)
        event.studyProgress(today) shouldBe 0f
    }

    test("studyProgress should compute progress with explicit studyPlanStart") {
        val event = DayEvent(
            title = "Exam",
            source = EventSource.MANUAL,
            category = AcademicCategory.FINALS,
            date = LocalDate(2026, 6, 10),
            studyPlanStart = "2026-06-05"
        )
        
        // Before start: 0f
        event.studyProgress(LocalDate(2026, 6, 4)) shouldBe 0f
        
        // On start: 0f
        event.studyProgress(LocalDate(2026, 6, 5)) shouldBe 0f
        
        // Midpoint: 2 days elapsed of 5 total days = 0.4f
        event.studyProgress(LocalDate(2026, 6, 7)) shouldBe 0.4f
        
        // On due date: 1f
        event.studyProgress(LocalDate(2026, 6, 10)) shouldBe 1f
        
        // After due date: 1f
        event.studyProgress(LocalDate(2026, 6, 12)) shouldBe 1f
    }

    test("studyProgress should fallback to 7 days before if studyPlanStart is null") {
        val event = DayEvent(
            title = "Deadline",
            source = EventSource.MANUAL,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 6, 10),
            studyPlanStart = null
        )
        
        // Fallback start is 2026-06-03
        // Before start: 0f
        event.studyProgress(LocalDate(2026, 6, 2)) shouldBe 0f
        
        // Midpoint: 3.5 days elapsed? Let's check exactly 2026-06-03 is 0f
        event.studyProgress(LocalDate(2026, 6, 3)) shouldBe 0f
        
        // 2026-06-07: 4 days elapsed of 7 total days
        event.studyProgress(LocalDate(2026, 6, 7)) shouldBe (4f / 7f)
        
        // On due date: 1f
        event.studyProgress(LocalDate(2026, 6, 10)) shouldBe 1f
    }
})
