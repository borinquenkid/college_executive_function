package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class SchedulingAlgorithmTest : FunSpec({
    test("should accept day event without conflicts") {
        val validator = ConstraintValidator()
        val detector = CollisionDetector()
        val algo = SchedulingAlgorithm(maxDepth = 3, validator, detector)
        
        val dayEvent = DayEvent(
            title = "Holiday",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            category = AcademicCategory.HOLIDAY
        )
        
        val result = algo.resolve(dayEvent, emptyList())
        
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        (result as ResolutionResult.Success).resolvedEvents[0].title shouldBe "Holiday"
    }

    test("should return conflict when max depth exceeded") {
        val validator = ConstraintValidator()
        val detector = CollisionDetector()
        val algo = SchedulingAlgorithm(maxDepth = 0, validator, detector)
        
        val event = TimeEvent(
            title = "Study",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            category = AcademicCategory.STUDY_BLOCK
        )
        
        val result = algo.resolve(event, emptyList(), depth = 1)
        
        result.shouldBeInstanceOf<ResolutionResult.Conflict>()
    }

    test("should accept multiple day events") {
        val validator = ConstraintValidator()
        val detector = CollisionDetector()
        val algo = SchedulingAlgorithm(maxDepth = 3, validator, detector)
        
        val event1 = DayEvent(
            title = "Event 1",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            category = AcademicCategory.REGULAR
        )
        
        val event2 = DayEvent(
            title = "Event 2",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 11),
            category = AcademicCategory.REGULAR
        )
        
        val result1 = algo.resolve(event1, emptyList())
        result1.shouldBeInstanceOf<ResolutionResult.Success>()
        
        val result2 = algo.resolve(event2, (result1 as ResolutionResult.Success).resolvedEvents)
        result2.shouldBeInstanceOf<ResolutionResult.Success>()
    }

    test("should handle priority comparison") {
        val validator = ConstraintValidator()
        val detector = CollisionDetector()
        val algo = SchedulingAlgorithm(maxDepth = 3, validator, detector)
        
        // FINALS has priority 100, STUDY_BLOCK has priority 10
        val finalist = DayEvent(
            title = "Final Exam",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            category = AcademicCategory.FINALS
        )
        
        val studyBlock = DayEvent(
            title = "Study Block",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            category = AcademicCategory.STUDY_BLOCK
        )
        
        finalist.priority shouldBe 100
        studyBlock.priority shouldBe 10
    }
})
