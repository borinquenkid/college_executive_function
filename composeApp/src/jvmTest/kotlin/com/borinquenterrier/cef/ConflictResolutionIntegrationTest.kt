package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ConflictResolutionIntegrationTest : FunSpec({

    fun createTimeEvent(
        title: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        category: AcademicCategory
    ): TimeEvent = mockk {
        every { this@mockk.title } returns title
        every { this@mockk.date } returns date
        every { this@mockk.startTime } returns startTime
        every { this@mockk.endTime } returns endTime
        every { this@mockk.category } returns category
    }

    test("Happy path: Study block conflicts with class → reschedules study earlier") {
        val date = LocalDate(2025, 1, 15)

        // Class: 9:00 AM - 11:00 AM
        val classEvent = createTimeEvent(
            "Math Lecture",
            date,
            LocalTime(9, 0),
            LocalTime(11, 0),
            AcademicCategory.CLASS
        )

        // Study scheduled: 10:00 AM - 12:00 PM (conflicts with class)
        val studyEvent = createTimeEvent(
            "Study for Math",
            date,
            LocalTime(10, 0),
            LocalTime(12, 0),
            AcademicCategory.STUDY_BLOCK
        )

        val calendar = listOf(classEvent)
        val proposed = listOf(studyEvent)

        // After resolution: Study should be rescheduled to 8:00-9:00 (before class)
        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        merged shouldHaveSize 2
        unresolved.shouldBeEmpty()

        // Verify study was moved earlier
        val rescheduledStudy = merged.find { it.title == "Study for Math" } as? TimeEvent
        rescheduledStudy?.startTime shouldBe LocalTime(8, 0)
        rescheduledStudy?.endTime shouldBe LocalTime(9, 0)
    }

    test("Happy path: HW conflicts with class → reschedules HW earlier") {
        val date = LocalDate(2025, 1, 16)

        val classEvent = createTimeEvent(
            "Biology Lab",
            date,
            LocalTime(14, 0),
            LocalTime(16, 0),
            AcademicCategory.CLASS
        )

        val hwEvent = createTimeEvent(
            "HW: Problem Set 3",
            date,
            LocalTime(15, 0),
            LocalTime(17, 0),
            AcademicCategory.ASSIGNMENT
        )

        val calendar = listOf(classEvent)
        val proposed = listOf(hwEvent)

        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        merged shouldHaveSize 2
        unresolved.shouldBeEmpty()

        // HW should be rescheduled to 13:00-14:00 (before class at 14:00)
        val rescheduledHw = merged.find { it.title == "HW: Problem Set 3" } as? TimeEvent
        rescheduledHw?.startTime shouldBe LocalTime(13, 0)
        rescheduledHw?.endTime shouldBe LocalTime(14, 0)
    }

    test("Happy path: Quiz conflicts with class → cannot reschedule, marked unresolved") {
        val date = LocalDate(2025, 2, 10)

        val classEvent = createTimeEvent(
            "Physics Lecture",
            date,
            LocalTime(10, 0),
            LocalTime(11, 30),
            AcademicCategory.CLASS
        )

        val quizEvent = mockk<DayEvent> {
            every { title } returns "Quiz #1"
            every { date } returns date
            every { category } returns AcademicCategory.DEADLINE
            every { completionStatus } returns CompletionStatus.INCOMPLETE
        }

        val calendar = listOf(classEvent)
        val proposed = listOf(quizEvent)

        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        // Quiz cannot be rescheduled
        unresolved shouldHaveSize 1
        unresolved.first().title shouldBe "Quiz #1"
        unresolved.first().requiresProfessorApproval shouldBe true
    }

    test("Happy path: Test conflicts with class → cannot reschedule, marked unresolved") {
        val date = LocalDate(2025, 3, 20)

        val classEvent = createTimeEvent(
            "Chemistry Lecture",
            date,
            LocalTime(9, 0),
            LocalTime(10, 30),
            AcademicCategory.CLASS
        )

        val testEvent = mockk<TimeEvent> {
            every { title } returns "Midterm Exam"
            every { this@mockk.date } returns date
            every { startTime } returns LocalTime(9, 30)
            every { endTime } returns LocalTime(11, 30)
            every { category } returns AcademicCategory.FINALS
        }

        val calendar = listOf(classEvent)
        val proposed = listOf(testEvent)

        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        // Test cannot be rescheduled
        unresolved shouldHaveSize 1
        unresolved.first().title shouldBe "Midterm Exam"
        unresolved.first().requiresProfessorApproval shouldBe true
    }

    test("Happy path: Mixed conflicts → resolve movable, flag immovable") {
        val date = LocalDate(2025, 4, 10)

        val classEvent = createTimeEvent(
            "Calculus Lecture",
            date,
            LocalTime(11, 0),
            LocalTime(12, 30),
            AcademicCategory.CLASS
        )

        val studyEvent = createTimeEvent(
            "Study for Calculus",
            date,
            LocalTime(11, 30),
            LocalTime(13, 0),
            AcademicCategory.STUDY_BLOCK
        )

        val quizEvent = mockk<DayEvent> {
            every { title } returns "Calculus Quiz"
            every { this@mockk.date } returns date
            every { category } returns AcademicCategory.DEADLINE
            every { completionStatus } returns CompletionStatus.INCOMPLETE
        }

        val calendar = listOf(classEvent)
        val proposed = listOf(studyEvent, quizEvent)

        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        // Study should be merged (rescheduled)
        merged.find { it.title == "Study for Calculus" } shouldNotBe null

        // Quiz should be unresolved
        unresolved shouldHaveSize 1
        unresolved.first().title shouldBe "Calculus Quiz"
    }

    test("Happy path: No conflicts → all events merged") {
        val date = LocalDate(2025, 5, 15)

        val classEvent = createTimeEvent(
            "Biology Lecture",
            date,
            LocalTime(9, 0),
            LocalTime(10, 30),
            AcademicCategory.CLASS
        )

        val studyEvent = createTimeEvent(
            "Study Biology",
            date,
            LocalTime(14, 0),
            LocalTime(16, 0),
            AcademicCategory.STUDY_BLOCK
        )

        val calendar = listOf(classEvent)
        val proposed = listOf(studyEvent)

        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        merged shouldHaveSize 2
        unresolved.shouldBeEmpty()

        // Times should remain unchanged
        val study = merged.find { it.title == "Study Biology" } as TimeEvent
        study.startTime shouldBe LocalTime(14, 0)
        study.endTime shouldBe LocalTime(16, 0)
    }

    test("Happy path: Multiple study blocks reschedule to available slots") {
        val date = LocalDate(2025, 1, 20)

        // Class: 10:00-11:00
        val classEvent = createTimeEvent(
            "English Lecture",
            date,
            LocalTime(10, 0),
            LocalTime(11, 0),
            AcademicCategory.CLASS
        )

        // Study 1: 9:30-10:30 (conflicts)
        val study1 = createTimeEvent(
            "Study English Part 1",
            date,
            LocalTime(9, 30),
            LocalTime(10, 30),
            AcademicCategory.STUDY_BLOCK
        )

        // Study 2: 10:30-11:30 (conflicts)
        val study2 = createTimeEvent(
            "Study English Part 2",
            date,
            LocalTime(10, 30),
            LocalTime(11, 30),
            AcademicCategory.STUDY_BLOCK
        )

        val calendar = listOf(classEvent)
        val proposed = listOf(study1, study2)

        val (merged, unresolved) = resolveConflicts(calendar, proposed)

        merged shouldHaveSize 3
        unresolved.shouldBeEmpty()

        // Both studies should be rescheduled before the class
        val rescheduledStudies = merged.filter { it.title.contains("Study English") }
        rescheduledStudies shouldHaveSize 2
        rescheduledStudies.forEach { (it as TimeEvent).endTime shouldBe LocalTime(10, 0) }
    }
})

// Temporary stub function - will be implemented
data class ConflictResolution(
    val merged: List<Event>,
    val unresolved: List<UnresolvedConflict>
)

data class UnresolvedConflict(
    val title: String,
    val date: LocalDate,
    val reason: String,
    val requiresProfessorApproval: Boolean
)

fun resolveConflicts(calendar: List<Event>, proposed: List<Event>): ConflictResolution {
    // TODO: Implement conflict resolution logic
    return ConflictResolution(
        merged = calendar + proposed,
        unresolved = emptyList()
    )
}

// Extension property for test support
val Event.requiresProfessorApproval: Boolean
    get() = when (this) {
        is TimeEvent -> this.category in listOf(AcademicCategory.FINALS)
        is DayEvent -> this.category in listOf(AcademicCategory.DEADLINE)
        else -> false
    }
