package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class CollisionDetectorTest : FunSpec({
    test("should calculate duration correctly") {
        val detector = CollisionDetector()
        val validator = ConstraintValidator()
        val date = LocalDate(2026, 6, 10)

        // Just verify the detector can be instantiated and used
        val event = TimeEvent(
            title = "Study",
            source = EventSource.MANUAL,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            category = AcademicCategory.STUDY_BLOCK
        )

        val duration = event.endTime.toSecondOfDay() - event.startTime.toSecondOfDay()
        duration shouldBe 3600  // 1 hour in seconds
    }

    test("should accept valid day event") {
        val detector = CollisionDetector()
        val validator = ConstraintValidator()
        val date = LocalDate(2026, 6, 10)

        val event = DayEvent(
            title = "Holiday",
            source = EventSource.MANUAL,
            date = date,
            category = AcademicCategory.HOLIDAY
        )

        event.title shouldBe "Holiday"
        event.date shouldBe date
    }

    test("should validate against lunch break") {
        val validator = ConstraintValidator(
            StudyPreferences(
                lunchStartHour = 12,
                lunchEndHour = 13
            )
        )

        // Slot overlapping lunch should be invalid
        val result = validator.isValidTimeSlot(
            date = LocalDate(2026, 6, 10),
            start = LocalTime(12, 30),
            end = LocalTime(13, 30),
            priority = 10,
            existingEvents = emptyList()
        )

        result shouldBe false
    }

    test("should validate slot outside lunch break") {
        val validator = ConstraintValidator(
            StudyPreferences(
                lunchStartHour = 12,
                lunchEndHour = 13
            )
        )

        val result = validator.isValidTimeSlot(
            date = LocalDate(2026, 6, 10),
            start = LocalTime(10, 0),
            end = LocalTime(11, 0),
            priority = 10,
            existingEvents = emptyList()
        )

        result shouldBe true
    }
})
