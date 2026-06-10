package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ConstraintValidatorTest : FunSpec({
    test("should validate slot within working hours") {
        val prefs = StudyPreferences(
            studyStartHour = 9,
            studyEndHour = 21,
            lunchStartHour = 12,
            lunchEndHour = 13,
            dinnerStartHour = 18,
            dinnerEndHour = 19
        )
        val validator = ConstraintValidator(prefs)
        val date = LocalDate(2026, 6, 10)

        val result = validator.isValidTimeSlot(
            date = date,
            start = LocalTime(10, 0),
            end = LocalTime(11, 0),
            priority = 10,
            existingEvents = emptyList()
        )

        result shouldBe true
    }

    test("should reject slot outside working hours") {
        val prefs = StudyPreferences(
            studyStartHour = 9,
            studyEndHour = 21,
            lunchStartHour = 12,
            lunchEndHour = 13,
            dinnerStartHour = 18,
            dinnerEndHour = 19
        )
        val validator = ConstraintValidator(prefs)
        val date = LocalDate(2026, 6, 10)

        val result = validator.isValidTimeSlot(
            date = date,
            start = LocalTime(8, 0),
            end = LocalTime(9, 0),
            priority = 10,
            existingEvents = emptyList()
        )

        result shouldBe false
    }

    test("should reject slot overlapping with lunch") {
        val prefs = StudyPreferences(
            studyStartHour = 9,
            studyEndHour = 21,
            lunchStartHour = 12,
            lunchEndHour = 13,
            dinnerStartHour = 18,
            dinnerEndHour = 19
        )
        val validator = ConstraintValidator(prefs)
        val date = LocalDate(2026, 6, 10)

        val result = validator.isValidTimeSlot(
            date = date,
            start = LocalTime(12, 30),
            end = LocalTime(13, 30),
            priority = 10,
            existingEvents = emptyList()
        )

        result shouldBe false
    }

    test("should reject slot overlapping with dinner") {
        val prefs = StudyPreferences(
            studyStartHour = 9,
            studyEndHour = 21,
            lunchStartHour = 12,
            lunchEndHour = 13,
            dinnerStartHour = 18,
            dinnerEndHour = 19
        )
        val validator = ConstraintValidator(prefs)
        val date = LocalDate(2026, 6, 10)

        val result = validator.isValidTimeSlot(
            date = date,
            start = LocalTime(17, 30),
            end = LocalTime(18, 30),
            priority = 10,
            existingEvents = emptyList()
        )

        result shouldBe false
    }

    test("should check day availability based on priority") {
        val validator = ConstraintValidator()
        val date = LocalDate(2026, 6, 10)
        val event = TimeEvent(
            title = "High Priority",
            source = EventSource.MANUAL,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            category = AcademicCategory.FINALS
        )

        val available =
            validator.isDayAvailable(date, priority = event.priority - 10, listOf(event))

        available shouldBe false
    }

    test("should allow day when no higher priority events exist") {
        val validator = ConstraintValidator()
        val date = LocalDate(2026, 6, 10)
        val event = TimeEvent(
            title = "Low Priority",
            source = EventSource.MANUAL,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            category = AcademicCategory.STUDY_BLOCK
        )

        val available =
            validator.isDayAvailable(date, priority = event.priority + 10, listOf(event))

        available shouldBe true
    }
})
