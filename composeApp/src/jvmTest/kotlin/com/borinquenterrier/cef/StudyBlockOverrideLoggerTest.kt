package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class StudyBlockOverrideLoggerTest : FunSpec({

    val date = LocalDate(2026, 9, 1)

    fun studyBlock(id: String, start: LocalTime = LocalTime(9, 0), end: LocalTime = LocalTime(10, 0)) = TimeEvent(
        id = id, title = "Study", source = EventSource.AI_GENERATED,
        category = AcademicCategory.STUDY_BLOCK,
        date = date, startTime = start, endTime = end
    )

    fun classEvent(id: String) = TimeEvent(
        id = id, title = "Class", source = EventSource.CLASS,
        category = AcademicCategory.CLASS,
        date = date, startTime = LocalTime(9, 0), endTime = LocalTime(10, 0)
    )

    // ── checkMove ────────────────────────────────────────────────────────────

    test("checkMove logs MOVE when study block changes date") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        val original = studyBlock("sb1")
        val moved = original.copy(date = LocalDate(2026, 9, 5))
        coEvery { localRepo.getAllEvents(any()) } returns listOf(original)

        StudyBlockOverrideLogger(localRepo, prefMemory).checkMove(moved, "default")

        coVerify(exactly = 1) { prefMemory.logOverride(OverrideAction.MOVE, original) }
    }

    test("checkMove logs MOVE when study block changes startTime") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        val original = studyBlock("sb1")
        val moved = original.copy(startTime = LocalTime(14, 0), endTime = LocalTime(15, 0))
        coEvery { localRepo.getAllEvents(any()) } returns listOf(original)

        StudyBlockOverrideLogger(localRepo, prefMemory).checkMove(moved, "default")

        coVerify(exactly = 1) { prefMemory.logOverride(OverrideAction.MOVE, original) }
    }

    test("checkMove does not log when study block is unchanged") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        val original = studyBlock("sb1")
        coEvery { localRepo.getAllEvents(any()) } returns listOf(original)

        StudyBlockOverrideLogger(localRepo, prefMemory).checkMove(original, "default")

        coVerify(exactly = 0) { prefMemory.logOverride(any(), any()) }
    }

    test("checkMove does not log for non-study-block events") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        val original = classEvent("c1")
        val moved = original.copy(date = LocalDate(2026, 9, 5))
        coEvery { localRepo.getAllEvents(any()) } returns listOf(original)

        StudyBlockOverrideLogger(localRepo, prefMemory).checkMove(moved, "default")

        coVerify(exactly = 0) { prefMemory.logOverride(any(), any()) }
    }

    test("checkMove does nothing when event id is not found locally") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        StudyBlockOverrideLogger(localRepo, prefMemory).checkMove(studyBlock("sb1"), "default")

        coVerify(exactly = 0) { prefMemory.logOverride(any(), any()) }
    }

    // ── checkDelete ──────────────────────────────────────────────────────────

    test("checkDelete logs DELETE when deleted event is a study block") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        val sb = studyBlock("sb2")
        coEvery { localRepo.getAllEvents(any()) } returns listOf(sb)

        StudyBlockOverrideLogger(localRepo, prefMemory).checkDelete("sb2", "default")

        coVerify(exactly = 1) { prefMemory.logOverride(OverrideAction.DELETE, sb) }
    }

    test("checkDelete does not log for non-study-block events") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        val cls = classEvent("c2")
        coEvery { localRepo.getAllEvents(any()) } returns listOf(cls)

        StudyBlockOverrideLogger(localRepo, prefMemory).checkDelete("c2", "default")

        coVerify(exactly = 0) { prefMemory.logOverride(any(), any()) }
    }

    test("checkDelete does nothing when event is not found") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val prefMemory = mockk<UserPreferenceMemoryRepository>(relaxed = true)
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        StudyBlockOverrideLogger(localRepo, prefMemory).checkDelete("sb99", "default")

        coVerify(exactly = 0) { prefMemory.logOverride(any(), any()) }
    }
})
