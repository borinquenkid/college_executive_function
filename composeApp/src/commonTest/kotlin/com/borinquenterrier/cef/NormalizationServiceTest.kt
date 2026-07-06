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

    test("registrar housekeeping dates are not miscategorized as DEADLINE/FINALS") {
        val service = NormalizationService()
        val baseDate = LocalDate(2026, 8, 2)

        // The AI extractor itself sometimes mistags these as DEADLINE/FINALS despite its own
        // prompt rules (bare "final"/"last day"/"due" keywords) — the normalizer must actively
        // correct that, not just avoid re-tagging an already-REGULAR event.
        fun aiMistagged(title: String, category: AcademicCategory) = DayEvent(
            title = title,
            source = EventSource.AI_GENERATED,
            category = category,
            date = baseDate
        )

        val lastDayOfTerm = aiMistagged("Last Day of Summer Term", AcademicCategory.DEADLINE)
        val finalGradesDue = aiMistagged("Final Grades Due", AcademicCategory.FINALS)
        val finalGradesClose = aiMistagged("Final Grades Close", AcademicCategory.FINALS)

        val result = service.extract(listOf(lastDayOfTerm, finalGradesDue, finalGradesClose))

        // Term boundary — not an actionable deadline, so it shouldn't get "Break It Down (AI)".
        result[0].category shouldBe AcademicCategory.SEMESTER_BOUND
        // Grade-posting housekeeping — not a graded submission or a final exam for the student.
        result[1].category shouldBe AcademicCategory.REGULAR
        result[2].category shouldBe AcademicCategory.REGULAR
    }

    test("a genuine final exam is still categorized as FINALS even with 'last day' nearby") {
        val service = NormalizationService()
        val examEvent = DayEvent(
            title = "Math Final Exam",
            source = EventSource.MANUAL,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2026, 12, 10)
        )

        val result = service.extract(listOf(examEvent))

        result[0].category shouldBe AcademicCategory.FINALS
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

