package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class NormalizationServiceTest : FunSpec({
    test("should extract and normalize event categories based on keywords") {
        val service = NormalizationService()
        val baseDate = LocalDate(2026, 6, 7)

        val holidayEvent = DayEvent(
            title = "Spring Break!",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = baseDate
        )
        val examEvent = DayEvent(
            title = "Math Final Exam",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = baseDate
        )
        val studyBlockEvent = DayEvent(
            title = "Study for exam",
            source = EventSource.MANUAL,
            category = AcademicCategory.STUDY_BLOCK,
            date = baseDate
        )

        val input = listOf(holidayEvent, examEvent, studyBlockEvent)
        val result = service.extract(input)

        result.size shouldBe 3
        result[0].category shouldBe AcademicCategory.HOLIDAY
        result[1].category shouldBe AcademicCategory.FINALS
        result[2].category shouldBe AcademicCategory.STUDY_BLOCK
    }

    test("should sanitize TimeEvents with invalid or flipped times") {
        val service = NormalizationService()
        val baseDate = LocalDate(2026, 6, 7)

        val validEvent = TimeEvent(
            title = "Valid Time Event",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = baseDate,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )
        val flippedEvent = TimeEvent(
            title = "Flipped Time Event",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = baseDate,
            startTime = LocalTime(15, 0),
            endTime = LocalTime(14, 0)
        )
        val midnightFlippedEvent = TimeEvent(
            title = "Midnight Flipped Event",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = baseDate,
            startTime = LocalTime(23, 59),
            endTime = LocalTime(23, 0)
        )

        val input = listOf(validEvent, flippedEvent, midnightFlippedEvent)
        val result = service.extract(input)

        result.size shouldBe 3

        // Valid remains unchanged
        (result[0] as TimeEvent).startTime shouldBe LocalTime(9, 0)
        (result[0] as TimeEvent).endTime shouldBe LocalTime(10, 0)

        // Flipped gets +1 hour
        (result[1] as TimeEvent).startTime shouldBe LocalTime(15, 0)
        (result[1] as TimeEvent).endTime shouldBe LocalTime(16, 0)

        // Midnight flipped gets 23:58 to 23:59
        (result[2] as TimeEvent).startTime shouldBe LocalTime(23, 58)
        (result[2] as TimeEvent).endTime shouldBe LocalTime(23, 59)
    }
})

