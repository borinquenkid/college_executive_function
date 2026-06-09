package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class CollisionResolverTest : FunSpec({

    val date = LocalDate(2026, 10, 1)
    val otherDate = LocalDate(2026, 10, 2)
    val resolver = CollisionResolver()

    test("resolve should succeed immediately if no overlap exists") {
        val existing = listOf(
            TimeEvent(
                title = "Class A",
                source = EventSource.CLASS,
                category = AcademicCategory.CLASS,
                date = date,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0)
            )
        )
        val newEvent = TimeEvent(
            title = "Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0)
        )

        val result = resolver.resolve(newEvent, existing)
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        success.resolvedEvents shouldHaveSize 1
        success.resolvedEvents.first().title shouldBe "Study Block"
    }

    test("resolve should shift low priority event if it collides with higher priority event") {
        val existing = listOf(
            TimeEvent(
                title = "Class A",
                source = EventSource.CLASS,
                category = AcademicCategory.CLASS,
                date = date,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0)
            )
        )
        val studyBlock = TimeEvent(
            title = "Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(10, 0), // Collides!
            endTime = LocalTime(11, 0)
        )

        val result = resolver.resolve(studyBlock, existing)
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        success.resolvedEvents shouldHaveSize 1
        val resolved = success.resolvedEvents.first() as TimeEvent
        resolved.title shouldBe "Study Block"
        // Should have shifted, e.g. same day but not 10:00-11:00 (10:00-11:00 collides, 12:00-13:00 is lunch)
        // 09:00-10:00 is available, so it should shift to 09:00-10:00
        resolved.startTime shouldBe LocalTime(9, 0)
        resolved.endTime shouldBe LocalTime(10, 0)
    }

    test("resolve should bump lower priority event and reschedule it recursively") {
        val existing = listOf(
            TimeEvent(
                title = "Study Block",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                date = date,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0)
            )
        )
        val newClass = TimeEvent(
            title = "Class A",
            source = EventSource.CLASS,
            category = AcademicCategory.CLASS,
            date = date,
            startTime = LocalTime(10, 0), // Collides!
            endTime = LocalTime(11, 0)
        )

        // Class A (priority 80) should bump Study Block (priority 10)
        val result = resolver.resolve(newClass, existing)
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        success.resolvedEvents shouldHaveSize 2
        
        val classEvent = success.resolvedEvents.find { it.category == AcademicCategory.CLASS } as TimeEvent
        val studyEvent = success.resolvedEvents.find { it.category == AcademicCategory.STUDY_BLOCK } as TimeEvent

        classEvent.startTime shouldBe LocalTime(10, 0) // Kept original time
        studyEvent.startTime shouldBe LocalTime(9, 0) // Shifted to next available slot
    }

    test("resolve should respect working hours and breaks") {
        val existing = listOf(
            TimeEvent(
                title = "Meeting",
                source = EventSource.MANUAL,
                category = AcademicCategory.REGULAR,
                date = date,
                startTime = LocalTime(9, 0),
                endTime = LocalTime(12, 0)
            )
        )
        val studyBlock = TimeEvent(
            title = "Study",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(11, 0)
        )

        // Meeting is 9-12. Lunch is 12-13.
        // Therefore, next available slot must start at 13:00.
        val result = resolver.resolve(studyBlock, existing)
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        val resolved = success.resolvedEvents.first() as TimeEvent
        resolved.startTime shouldBe LocalTime(13, 0)
    }

    test("resolve should shift forward with a warning if no backward/current slot exists") {
        // Completely fill the day and the previous 7 days
        val existing = mutableListOf<Event>()
        for (i in 0..7) {
            val d = date.minus(i, DateTimeUnit.DAY)
            existing.add(
                TimeEvent(
                    title = "Busy Day",
                    source = EventSource.MANUAL,
                    category = AcademicCategory.REGULAR,
                    date = d,
                    startTime = LocalTime(9, 0),
                    endTime = LocalTime(21, 0)
                )
            )
        }

        val timeEvent = TimeEvent(
            title = "Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(11, 0)
        )

        val result = resolver.resolve(timeEvent, existing)
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        val resolved = success.resolvedEvents.first()
        resolved.date shouldBe date.plus(1, DateTimeUnit.DAY)
        resolved.warning shouldBe "Study block scheduled late/after deadline"
    }

    test("resolve should fail with Conflict if recursion depth is exceeded") {
        val tightResolver = CollisionResolver(maxDepth = 1)
        // We create a tight schedule where bumping A bumps B, which bumps C, which exceeds maxDepth 1.
        val existing = listOf(
            TimeEvent(
                title = "Study 1",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.REGULAR, // priority 30
                date = date,
                startTime = LocalTime(9, 0),
                endTime = LocalTime(10, 0)
            ),
            TimeEvent(
                title = "Study 2",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK, // priority 10
                date = date,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0)
            )
        )

        val finalExam = TimeEvent(
            title = "Final Exam",
            source = EventSource.SCHOOL,
            category = AcademicCategory.FINALS, // priority 100
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )

        val result = tightResolver.resolve(finalExam, existing)
        result.shouldBeInstanceOf<ResolutionResult.Conflict>()
    }

    test("resolve should respect custom working hours and breaks from preferences") {
        val customPrefs = StudyPreferences(
            studyStartHour = 10,
            studyEndHour = 18,
            lunchStartHour = 13,
            lunchEndHour = 14,
            dinnerStartHour = 16,
            dinnerEndHour = 17,
            maxStudyBlockHours = 2,
            preferredBreakMinutes = 30
        )
        val customResolver = CollisionResolver(preferences = customPrefs)

        val existing = listOf(
            TimeEvent(
                title = "Meeting",
                source = EventSource.MANUAL,
                category = AcademicCategory.REGULAR,
                date = date,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(13, 0)
            )
        )
        val studyBlock = TimeEvent(
            title = "Study",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(12, 0)
        )

        val result = customResolver.resolve(studyBlock, existing)
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        val resolved = success.resolvedEvents.first() as TimeEvent
        resolved.startTime shouldBe LocalTime(14, 0)
    }

    test("resolve should respect user preference constraints") {
        // Date is 2026-10-01 which is a Thursday
        val thursday = LocalDate(2026, 10, 1)
        val constraints = listOf(
            UserPreferenceConstraint(
                dayOfWeek = kotlinx.datetime.DayOfWeek.THURSDAY,
                startHour = 14,
                endHour = 16
            )
        )
        val customResolver = CollisionResolver(
            preferences = StudyPreferences(studyStartHour = 9, studyEndHour = 17),
            userConstraints = constraints
        )

        // Try to schedule a study block from 14:00 to 15:00 (inside constraint)
        val studyBlock = TimeEvent(
            title = "Study",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = thursday,
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0)
        )

        val result = customResolver.resolve(studyBlock, emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val success = result as ResolutionResult.Success
        val resolved = success.resolvedEvents.first() as TimeEvent
        // Should have shifted outside of 14:00-16:00 range
        // Since study Start is 9:00, 9:00-10:00 is available!
        resolved.startTime shouldBe LocalTime(9, 0)
    }
})
