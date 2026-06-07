package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.datetime.LocalDate

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
})
