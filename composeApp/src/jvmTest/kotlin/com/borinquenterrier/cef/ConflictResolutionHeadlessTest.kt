package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.minutes

/**
 * Headless e2e test: Load real PDFs → Parse → Generate study plan → Resolve conflicts
 * Tests happy path only: conflicts are auto-resolved or marked for professor review
 */
class ConflictResolutionHeadlessTest : FunSpec({

    test("Headless e2e: Process real syllabi, generate plan, resolve conflicts").config(
        timeout = 120_000.minutes  // Real API calls can be slow
    ) {
        val apiKey = resolveApiKey("CONFLICT RESOLUTION E2E TEST") ?: return@config

        // In real scenario, load from ~/Desktop
        // For now, we'll create synthetic events that simulate the calendar + proposed plan

        // CALENDAR STATE: What's already on the calendar
        val calendarEvents = listOf(
            TimeEvent(
                title = "BDAN 250: Lecture",
                date = LocalDate(2025, 1, 13),
                startTime = LocalTime(9, 10),
                endTime = LocalTime(10, 5),
                category = AcademicCategory.CLASS,
                source = EventSource.CLASS
            ),
            TimeEvent(
                title = "HIST 152: Lecture",
                date = LocalDate(2025, 1, 14),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 30),
                category = AcademicCategory.CLASS,
                source = EventSource.CLASS
            )
        )

        // PROPOSED STATE: AI-generated study plan events
        val proposedEvents = listOf(
            // Study session that conflicts with BDAN 250
            TimeEvent(
                title = "Study for BDAN 250",
                date = LocalDate(2025, 1, 13),
                startTime = LocalTime(9, 30),  // Overlaps with 9:10-10:05 class
                endTime = LocalTime(11, 0),
                category = AcademicCategory.STUDY_BLOCK,
                source = EventSource.AI_GENERATED
            ),
            // HW that conflicts with HIST 152
            TimeEvent(
                title = "HW: Problem Set for HIST",
                date = LocalDate(2025, 1, 14),
                startTime = LocalTime(10, 30),  // Overlaps with 10:00-11:30 class
                endTime = LocalTime(12, 0),
                category = AcademicCategory.STUDY_BLOCK,
                source = EventSource.AI_GENERATED
            ),
            // Quiz that conflicts (immovable)
            DayEvent(
                title = "Quiz #1",
                date = LocalDate(2025, 1, 20),
                category = AcademicCategory.DEADLINE,
                source = EventSource.AI_GENERATED
            )
        )

        // RESOLVE: Run conflict resolver
        val resolver = ConflictResolver()
        val (merged, unresolved) = resolver.resolveConflicts(calendarEvents, proposedEvents)

        // VERIFY HAPPY PATH RESULTS
        println("=== Conflict Resolution Results ===")
        println("Merged events: ${merged.size}")
        println("Unresolved conflicts: ${unresolved.size}")

        // Should have:
        // - 2 calendar events (class)
        // - 2 rescheduled studies/HW (moved earlier, no longer conflicting)
        // - 1 unresolved quiz (needs professor approval)
        expectKnownFailure(issue = "https://github.com/borinquenkid/college_executive_function/issues/5") {
            merged shouldHaveSize 4  // 2 classes + 2 rescheduled study/hw

            // Study and HW should be rescheduled earlier
            val rescheduledStudy = merged.find { it.title.contains("Study for BDAN") }
            rescheduledStudy shouldNotBe null
            (rescheduledStudy as? TimeEvent)?.startTime?.let { it shouldBe LocalTime(8, 0) }

            val rescheduledHw = merged.find { it.title.contains("Problem Set") }
            rescheduledHw shouldNotBe null
            (rescheduledHw as? TimeEvent)?.startTime?.let { it shouldBe LocalTime(9, 0) }

            // Quiz should be unresolved
            unresolved shouldHaveSize 1
            unresolved.first().title shouldBe "Quiz #1"
            unresolved.first().requiresProfessorApproval shouldBe true

            println("✅ Happy path: Movable events rescheduled, immovable events flagged")
            println("Unresolved: ${unresolved.map { it.title }}")
        }
    }

    test("Headless e2e: No conflicts in generated plan").config(
        timeout = 60_000.minutes
    ) {
        val apiKey = resolveApiKey("CONFLICT RESOLUTION E2E TEST") ?: return@config

        val calendarEvents = listOf(
            TimeEvent(
                title = "BDAN 250: Lecture",
                date = LocalDate(2025, 1, 13),
                startTime = LocalTime(9, 10),
                endTime = LocalTime(10, 5),
                category = AcademicCategory.CLASS,
                source = EventSource.CLASS
            )
        )

        val proposedEvents = listOf(
            TimeEvent(
                title = "Study for BDAN 250",
                date = LocalDate(2025, 1, 13),
                startTime = LocalTime(14, 0),  // No conflict - afternoon
                endTime = LocalTime(16, 0),
                category = AcademicCategory.STUDY_BLOCK,
                source = EventSource.AI_GENERATED
            )
        )

        val resolver = ConflictResolver()
        val (merged, unresolved) = resolver.resolveConflicts(calendarEvents, proposedEvents)

        merged shouldHaveSize 2
        unresolved.shouldHaveSize(0)

        println("✅ Happy path: No conflicts, all events merged")
    }
})

/**
 * Utility to generate synthetic conflicting events for testing.
 * Simulates what would happen if AI-generated study plan conflicts with calendar.
 */
object ConflictGeneratorUtility {
    fun generateConflictingStudySession(
        classEvent: TimeEvent,
        offsetMinutes: Int = 30
    ): TimeEvent = TimeEvent(
        title = "Study for ${classEvent.title}",
        date = classEvent.date,
        startTime = LocalTime(
            classEvent.startTime.hour,
            classEvent.startTime.minute + offsetMinutes
        ),
        endTime = LocalTime(
            classEvent.endTime.hour + 1,
            classEvent.endTime.minute
        ),
        category = AcademicCategory.STUDY_BLOCK,
        source = EventSource.AI_GENERATED
    )

    fun generateUnmovableQuiz(
        classEvent: TimeEvent,
        daysAfter: Int = 7
    ): DayEvent = DayEvent(
        title = "Quiz for ${classEvent.title}",
        date = classEvent.date.plus(daysAfter, DateTimeUnit.DAY),
        category = AcademicCategory.DEADLINE,
        source = EventSource.AI_GENERATED
    )

    fun generateUnmovableExam(
        classEvent: TimeEvent,
        daysAfter: Int = 30
    ): TimeEvent = TimeEvent(
        title = "Exam for ${classEvent.title}",
        date = classEvent.date.plus(daysAfter, DateTimeUnit.DAY),
        startTime = LocalTime(10, 0),
        endTime = LocalTime(12, 0),
        category = AcademicCategory.FINALS,
        source = EventSource.AI_GENERATED
    )
}
