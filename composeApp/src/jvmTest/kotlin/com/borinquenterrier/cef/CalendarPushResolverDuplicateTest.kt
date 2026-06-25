package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/**
 * Regression tests for the duplicate-event bug:
 *
 * When the same event is re-extracted (AI title may vary in casing or gain/lose a
 * "Submit " prefix), the Phase-2 synced-filter in CalendarPushResolver must recognise
 * the canonical form and suppress the re-push. Previously, an exact case-sensitive
 * title comparison let these slips through, creating duplicates in the calendar.
 */
class CalendarPushResolverDuplicateTest : FunSpec({

    fun deadline(id: String, title: String, date: LocalDate, syncStatus: SyncStatus = SyncStatus.SYNCED) =
        DayEvent(
            id = id,
            title = title,
            date = date,
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            syncStatus = syncStatus
        )

    fun resolver(repo: CalendarAgent) = CalendarPushResolver(repo)

    // ── Duplicate suppression ────────────────────────────────────────────────

    test("event with different title casing is not re-pushed") {
        val synced = deadline("id1", "Homework 1 Due", LocalDate(2024, 3, 15))
        val aiVariant = deadline("id2", "homework 1 due", LocalDate(2024, 3, 15))

        val repo = mockk<CalendarAgent>(relaxed = true)
        runBlocking { resolver(repo).resolveAndPush(listOf(aiVariant), listOf(synced), "default") }

        coVerify(exactly = 0) { repo.saveEvent(any(), any()) }
    }

    test("event with Submit prefix variant is not re-pushed") {
        val synced = deadline("id1", "Chapter 5 Review", LocalDate(2024, 3, 20))
        val aiVariant = deadline("id2", "Submit Chapter 5 Review", LocalDate(2024, 3, 20))

        val repo = mockk<CalendarAgent>(relaxed = true)
        runBlocking { resolver(repo).resolveAndPush(listOf(aiVariant), listOf(synced), "default") }

        coVerify(exactly = 0) { repo.saveEvent(any(), any()) }
    }

    test("event with Complete prefix variant is not re-pushed") {
        val synced = deadline("id1", "Lab Report 2", LocalDate(2024, 4, 1))
        val aiVariant = deadline("id2", "Complete Lab Report 2", LocalDate(2024, 4, 1))

        val repo = mockk<CalendarAgent>(relaxed = true)
        runBlocking { resolver(repo).resolveAndPush(listOf(aiVariant), listOf(synced), "default") }

        coVerify(exactly = 0) { repo.saveEvent(any(), any()) }
    }

    test("identical event pushed twice is saved only once") {
        val synced = deadline("id1", "HW 1 Due", LocalDate(2024, 3, 15))
        val same = deadline("id1", "HW 1 Due", LocalDate(2024, 3, 15))

        val repo = mockk<CalendarAgent>(relaxed = true)
        runBlocking { resolver(repo).resolveAndPush(listOf(same), listOf(synced), "default") }

        coVerify(exactly = 0) { repo.saveEvent(any(), any()) }
    }

    // ── Legitimate new events still go through ───────────────────────────────

    test("event with different title on same date IS pushed") {
        val synced = deadline("id1", "Homework 1 Due", LocalDate(2024, 3, 15))
        val newEvent = deadline("id2", "Homework 2 Due", LocalDate(2024, 3, 15))

        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.saveEvent(any(), any()) } just Runs

        runBlocking { resolver(repo).resolveAndPush(listOf(newEvent), listOf(synced), "default") }

        coVerify(exactly = 1) { repo.saveEvent(any(), any()) }
    }

    test("same canonical title on a different date IS pushed") {
        val synced = deadline("id1", "Homework 1 Due", LocalDate(2024, 3, 15))
        val nextMonth = deadline("id2", "Homework 1 Due", LocalDate(2024, 4, 15))

        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.saveEvent(any(), any()) } just Runs

        runBlocking { resolver(repo).resolveAndPush(listOf(nextMonth), listOf(synced), "default") }

        coVerify(exactly = 1) { repo.saveEvent(any(), any()) }
    }

    test("batch with mixed duplicates and new events pushes only new events") {
        val synced1 = deadline("id1", "Exam 1 Review", LocalDate(2024, 3, 10))
        val synced2 = deadline("id2", "Project Proposal", LocalDate(2024, 3, 20))

        val duplicate = deadline("id9", "exam 1 review", LocalDate(2024, 3, 10))  // casing variant
        val genuineNew = deadline("id3", "Final Presentation", LocalDate(2024, 4, 30))

        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.saveEvent(any(), any()) } just Runs

        runBlocking {
            resolver(repo).resolveAndPush(
                events = listOf(duplicate, genuineNew),
                existing = listOf(synced1, synced2),
                calendarId = "default"
            )
        }

        // Only the genuinely new event should be saved
        coVerify(exactly = 1) { repo.saveEvent(any(), any()) }
    }
})
