package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ConflictResolverTest : FunSpec({

    val resolver = ConflictResolver()
    val date = LocalDate(2026, 7, 1)

    fun classAt(start: Int, end: Int) = TimeEvent(
        id = null, title = "Class", source = EventSource.MANUAL,
        category = AcademicCategory.CLASS,
        startTime = LocalTime(start, 0), endTime = LocalTime(end, 0), date = date
    )

    fun studyAt(start: Int, end: Int) = TimeEvent(
        id = null, title = "Study", source = EventSource.AI_GENERATED,
        category = AcademicCategory.STUDY_BLOCK,
        startTime = LocalTime(start, 0), endTime = LocalTime(end, 0), date = date
    )

    fun deadline(title: String = "Essay due") = DayEvent(
        title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, date = date
    )

    // ── overlap detection ──────────────────────────────────────────────────────

    test("TimeEvents overlap when time ranges intersect") {
        classAt(9, 11).overlaps(classAt(10, 12)) shouldBe true
    }

    test("TimeEvents do not overlap when adjacent") {
        classAt(9, 10).overlaps(classAt(10, 11)) shouldBe false
    }

    test("TimeEvents on different dates never overlap") {
        val e1 = TimeEvent(id=null, title="A", source=EventSource.MANUAL,
            startTime=LocalTime(9,0), endTime=LocalTime(10,0), date=LocalDate(2026,7,1))
        val e2 = TimeEvent(id=null, title="B", source=EventSource.MANUAL,
            startTime=LocalTime(9,0), endTime=LocalTime(10,0), date=LocalDate(2026,7,2))
        e1.overlaps(e2) shouldBe false
    }

    // ── DEADLINE transparency ─────────────────────────────────────────────────

    test("DEADLINE always added even when it overlaps a TimeEvent") {
        val classAt9 = classAt(9, 10)
        // A DayEvent overlaps() always returns false so we test with a TimeEvent DEADLINE
        val deadlineAt9 = TimeEvent(
            id = null, title = "Essay due", source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            startTime = LocalTime(9, 0), endTime = LocalTime(10, 0), date = date
        )
        val (merged, unresolved) = resolver.resolveConflicts(listOf(classAt9), listOf(deadlineAt9))
        merged.any { it.title == "Essay due" } shouldBe true
        unresolved.shouldBeEmpty()
    }

    test("DEADLINE DayEvent never blocks other events") {
        val deadlineDay = deadline()
        val studyAt9 = studyAt(9, 10)
        // Existing calendar has a DEADLINE day event; study should not conflict with it
        val (merged, unresolved) = resolver.resolveConflicts(listOf(deadlineDay), listOf(studyAt9))
        merged.any { it.title == "Study" } shouldBe true
        unresolved.shouldBeEmpty()
    }

    // ── rescheduling ──────────────────────────────────────────────────────────

    test("STUDY_BLOCK rescheduled earlier when earlier slot is free") {
        val classAt10 = classAt(10, 11)
        val studyAt10 = studyAt(10, 11) // conflicts

        val (merged, unresolved) = resolver.resolveConflicts(listOf(classAt10), listOf(studyAt10))

        unresolved.shouldBeEmpty()
        val rescheduled = merged.filterIsInstance<TimeEvent>().find { it.title == "Study" }
        rescheduled shouldNotBe null
        rescheduled!!.startTime.hour shouldBe 9 // moved back one hour
    }

    test("STUDY_BLOCK rescheduled forward when no earlier slot is free") {
        // Class at 8–9 and 9–10 blocks all earlier slots; study wants 9–10
        val class1 = classAt(8, 9)
        val class2 = classAt(9, 10)
        val studyAt9 = studyAt(9, 10)

        val (merged, unresolved) = resolver.resolveConflicts(listOf(class1, class2), listOf(studyAt9))

        unresolved.shouldBeEmpty()
        val rescheduled = merged.filterIsInstance<TimeEvent>().find { it.title == "Study" }
        rescheduled shouldNotBe null
        rescheduled!!.startTime.hour shouldBe 10 // moved forward
    }

    test("STUDY_BLOCK added as-is when no slot found in either direction") {
        // Pack 8 AM–9 PM solid so there is nowhere to go
        val calendar = (8..20).map { classAt(it, it + 1) }
        val studyAt12 = studyAt(12, 13)

        val (merged, unresolved) = resolver.resolveConflicts(calendar, listOf(studyAt12))

        unresolved.shouldBeEmpty() // not surfaced as a dialog conflict
        merged.any { it.title == "Study" } shouldBe true // added anyway
    }

    test("REGULAR event is also treated as movable") {
        val classAt10 = classAt(10, 11)
        val regularAt10 = TimeEvent(
            id = null, title = "Peer review", source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            startTime = LocalTime(10, 0), endTime = LocalTime(11, 0), date = date
        )

        val (merged, unresolved) = resolver.resolveConflicts(listOf(classAt10), listOf(regularAt10))

        unresolved.shouldBeEmpty()
        merged.any { it.title == "Peer review" } shouldBe true
    }

    // ── immovable events ──────────────────────────────────────────────────────

    test("CLASS event conflict goes to unresolved with requiresProfessorApproval=true") {
        val existingClass = classAt(10, 11)
        val newClass = classAt(10, 11).copy(title = "Make-up Class")

        val (_, unresolved) = resolver.resolveConflicts(listOf(existingClass), listOf(newClass))

        unresolved shouldHaveSize 1
        unresolved[0].requiresProfessorApproval shouldBe true
        unresolved[0].title shouldBe "Make-up Class"
    }

    test("FINALS event conflict goes to unresolved") {
        val classAt10 = classAt(10, 12)
        val final = TimeEvent(
            id = null, title = "Final Exam", source = EventSource.AI_GENERATED,
            category = AcademicCategory.FINALS,
            startTime = LocalTime(10, 0), endTime = LocalTime(12, 0), date = date
        )

        val (_, unresolved) = resolver.resolveConflicts(listOf(classAt10), listOf(final))

        unresolved shouldHaveSize 1
        unresolved[0].title shouldBe "Final Exam"
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    test("empty calendar accepts all proposed events") {
        val (merged, unresolved) = resolver.resolveConflicts(emptyList(), listOf(studyAt(9, 10)))
        merged shouldHaveSize 1
        unresolved.shouldBeEmpty()
    }

    test("empty proposed list returns only calendar events") {
        val (merged, unresolved) = resolver.resolveConflicts(listOf(classAt(9, 10)), emptyList())
        merged shouldHaveSize 1
        unresolved.shouldBeEmpty()
    }

    test("non-conflicting events all added to merged") {
        val (merged, unresolved) = resolver.resolveConflicts(
            listOf(classAt(9, 10)),
            listOf(studyAt(11, 12), deadline())
        )
        merged shouldHaveSize 3
        unresolved.shouldBeEmpty()
    }
})
