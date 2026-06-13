package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class GoogleRemoteCalendarRepositoryTest : FunSpec({

    val syncService = mockk<GoogleCalendarSyncService>(relaxed = true)
    val idResolver = mockk<CalendarIdResolver>(relaxed = true)
    val conflictDetector = mockk<EventConflictDetector>(relaxed = true)
    val eventFilter = mockk<EventRangeFilter>(relaxed = true)
    val repo = GoogleRemoteCalendarRepository(
        syncService,
        idResolver,
        conflictDetector,
        eventFilter
    )

    val cefCalId = "cef-calendar-id-123"
    val date = LocalDate(2026, 6, 8)

    val timeEvent = TimeEvent(
        id = "evt-1",
        title = "Lecture",
        source = EventSource.CLASS,
        date = date,
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0),
        syncStatus = SyncStatus.SYNCED,
        category = AcademicCategory.CLASS
    )

    val dayEvent = DayEvent(
        id = "evt-day",
        title = "Holiday",
        source = EventSource.SCHOOL,
        date = date,
        syncStatus = SyncStatus.SYNCED,
        category = AcademicCategory.HOLIDAY
    )

    beforeEach {
        clearAllMocks()
        coEvery { idResolver.resolveCalendarId(any<String>()) } answers {
            val id = firstArg<String>()
            if (id == "default") cefCalId else id
        }
        coEvery { eventFilter.filterByDateRange(any(), any(), any()) } answers { firstArg() }
        coEvery { eventFilter.filterBySyncStatus(any(), any()) } answers { firstArg() }
        coEvery { eventFilter.filterIncompleteBeforeDate(any(), any()) } answers { firstArg() }
        coEvery { conflictDetector.validateNoConflict(any(), any()) } just runs
    }

    // ─── getAllEvents ────────────────────────────────────────────────────────

    test("getAllEvents resolves 'default' to CEF calendar ID") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(timeEvent)

        val result = repo.getAllEvents("default")

        result shouldHaveSize 1
        coVerify(exactly = 1) { syncService.getEvents(cefCalId) }
    }

    test("getAllEvents uses provided calendarId directly when not 'default'") {
        val specificId = "specific-cal-id"
        coEvery { syncService.getEvents(specificId) } returns listOf(dayEvent)

        val result = repo.getAllEvents(specificId)

        result shouldHaveSize 1
        coVerify(exactly = 0) { syncService.listCalendars() }
        coVerify(exactly = 1) { syncService.getEvents(specificId) }
    }

    // ─── saveEvent ───────────────────────────────────────────────────────────

    test("saveEvent syncs event when no overlap exists") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns emptyList()
        coEvery { syncService.syncEvent(any(), any()) } returns ""

        repo.saveEvent(timeEvent, "default")

        coVerify(exactly = 1) { syncService.syncEvent(timeEvent, cefCalId) }
    }

    test("saveEvent throws OverlapException when event overlaps existing") {
        val conflictingEvent = timeEvent.copy(id = "existing-evt", title = "Conflict")
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(conflictingEvent)
        coEvery { conflictDetector.validateNoConflict(timeEvent, listOf(conflictingEvent)) } throws
            OverlapException(conflictingEvent, timeEvent)

        val ex = shouldThrow<OverlapException> {
            repo.saveEvent(timeEvent, "default")
        }

        ex.existingEvent shouldBe conflictingEvent
        ex.newEvent shouldBe timeEvent
        coVerify(exactly = 0) { syncService.syncEvent(any(), any()) }
    }

    test("saveEvent does not conflict with itself (same id)") {
        // If the event being saved already exists in the calendar with the same id, no overlap
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(timeEvent) // same id
        coEvery { syncService.syncEvent(any(), any()) } returns ""

        // Should NOT throw because conflict check filters out same id
        repo.saveEvent(timeEvent, "default")

        coVerify(exactly = 1) { syncService.syncEvent(timeEvent, cefCalId) }
    }

    // ─── updateEvent ─────────────────────────────────────────────────────────

    test("updateEvent syncs event to resolved calendar ID") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.syncEvent(any(), any()) } returns ""

        repo.updateEvent(timeEvent, "default")

        coVerify(exactly = 1) { syncService.syncEvent(timeEvent, cefCalId) }
    }

    // ─── deleteEvent ─────────────────────────────────────────────────────────

    test("deleteEvent successfully deletes event") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.deleteEvent(any(), any()) } just runs

        repo.deleteEvent("evt-1", "default")

        coVerify(exactly = 1) { syncService.deleteEvent(cefCalId, "evt-1") }
    }

    test("deleteEvent swallows GoogleApiException with 410 status") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.deleteEvent(any(), any()) } throws GoogleApiException(410, "Gone")

        // Should NOT throw — 410 is swallowed
        repo.deleteEvent("evt-1", "default")
    }

    test("deleteEvent rethrows GoogleApiException with non-410 status") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.deleteEvent(any(), any()) } throws GoogleApiException(
            500,
            "Internal Server Error"
        )

        shouldThrow<GoogleApiException> {
            repo.deleteEvent("evt-1", "default")
        }.statusCode shouldBe 500
    }

    // ─── hardDeleteEvent ─────────────────────────────────────────────────────

    test("hardDeleteEvent delegates to deleteEvent") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.deleteEvent(any(), any()) } just runs

        repo.hardDeleteEvent("evt-1", "default")

        coVerify(exactly = 1) { syncService.deleteEvent(cefCalId, "evt-1") }
    }

    // ─── clearCalendar ───────────────────────────────────────────────────────

    test("clearCalendar deletes all events in the calendar") {
        val e1 = timeEvent.copy(id = "id-1")
        val e2 =
            timeEvent.copy(id = "id-2", startTime = LocalTime(11, 0), endTime = LocalTime(12, 0))
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(e1, e2)
        coEvery { syncService.deleteEvent(any(), any()) } just runs

        repo.clearCalendar("default")

        coVerify(exactly = 1) { syncService.deleteEvent(cefCalId, "id-1") }
        coVerify(exactly = 1) { syncService.deleteEvent(cefCalId, "id-2") }
    }

    test("clearCalendar swallows 410 Gone for already-deleted events") {
        val e1 = timeEvent.copy(id = "gone-id")
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(e1)
        coEvery { syncService.deleteEvent(cefCalId, "gone-id") } throws GoogleApiException(
            410,
            "Gone"
        )

        // Should NOT throw
        repo.clearCalendar("default")
    }

    test("clearCalendar skips events with null id") {
        val nullIdEvent = timeEvent.copy(id = null)
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(nullIdEvent)

        repo.clearCalendar("default")

        coVerify(exactly = 0) { syncService.deleteEvent(any(), any()) }
    }

    // ─── getEventsInRange ────────────────────────────────────────────────────

    test("getEventsInRange returns only events within the date range") {
        val inRange = timeEvent.copy(date = LocalDate(2026, 6, 10))
        val outOfRange = timeEvent.copy(id = "out", date = LocalDate(2026, 7, 1))
        val start = LocalDate(2026, 6, 1)
        val end = LocalDate(2026, 6, 30)
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(inRange, outOfRange)
        coEvery { eventFilter.filterByDateRange(any(), start, end) } answers {
            firstArg<List<Event>>().filter { event ->
                event.date in start..end
            }
        }

        val result = repo.getEventsInRange(
            start = start,
            end = end,
            calendarId = "default"
        )

        result shouldHaveSize 1
        result.first().id shouldBe inRange.id
    }

    test("getEventsInRange works correctly for DayEvent types") {
        val inRangeDay = dayEvent.copy(date = LocalDate(2026, 6, 15))
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(inRangeDay)

        val result = repo.getEventsInRange(
            start = LocalDate(2026, 6, 1),
            end = LocalDate(2026, 6, 30),
            calendarId = "default"
        )

        result shouldHaveSize 1
        result.first().shouldBeInstanceOf<DayEvent>()
    }

    test("getEventsInRange returns empty list when no events are in range") {
        val start = LocalDate(2026, 6, 1)
        val end = LocalDate(2026, 6, 30)
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(
            timeEvent.copy(date = LocalDate(2025, 1, 1))
        )
        coEvery { eventFilter.filterByDateRange(any(), start, end) } answers {
            firstArg<List<Event>>().filter { event ->
                event.date in start..end
            }
        }

        val result = repo.getEventsInRange(
            start = start,
            end = end,
            calendarId = "default"
        )

        result.shouldBeEmpty()
    }

    // ─── getEventsBySyncStatus ───────────────────────────────────────────────

    test("getEventsBySyncStatus returns all events when status is SYNCED") {
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(timeEvent, dayEvent)

        val result = repo.getEventsBySyncStatus(SyncStatus.SYNCED, "default")

        result shouldHaveSize 2
    }

    test("getEventsBySyncStatus returns empty list for non-SYNCED status") {
        val result = repo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, "default")
        result.shouldBeEmpty()

        val result2 = repo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY, "default")
        result2.shouldBeEmpty()
    }

    // ─── getIncompleteEventsBefore ───────────────────────────────────────────

    test("getIncompleteEventsBefore returns only incomplete events before the given date") {
        val cutoffDate = LocalDate(2026, 6, 8)
        val pastIncomplete = timeEvent.copy(
            id = "past-incomplete",
            date = LocalDate(2026, 5, 1),
            completionStatus = CompletionStatus.INCOMPLETE
        )
        val futureIncomplete = timeEvent.copy(
            id = "future-incomplete",
            date = LocalDate(2026, 8, 1),
            completionStatus = CompletionStatus.INCOMPLETE
        )
        val pastComplete = timeEvent.copy(
            id = "past-complete",
            date = LocalDate(2026, 5, 1),
            completionStatus = CompletionStatus.COMPLETED
        )
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(
            pastIncomplete,
            futureIncomplete,
            pastComplete
        )
        coEvery { eventFilter.filterIncompleteBeforeDate(any(), cutoffDate) } answers {
            firstArg<List<Event>>().filter { event ->
                event.completionStatus == CompletionStatus.INCOMPLETE && event.date < cutoffDate
            }
        }

        val result = repo.getIncompleteEventsBefore(cutoffDate, "default")

        result shouldHaveSize 1
        result.first().id shouldBe "past-incomplete"
    }

    test("getIncompleteEventsBefore returns empty list when all events are complete or in the future") {
        val cutoffDate = LocalDate(2026, 6, 8)
        val completeEvent = timeEvent.copy(
            date = LocalDate(2026, 5, 1),
            completionStatus = CompletionStatus.COMPLETED
        )
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(cefCalId, "CEF Academic")
        )
        coEvery { syncService.getEvents(cefCalId) } returns listOf(completeEvent)
        coEvery { eventFilter.filterIncompleteBeforeDate(any(), cutoffDate) } answers {
            firstArg<List<Event>>().filter { event ->
                event.completionStatus == CompletionStatus.INCOMPLETE && event.date < cutoffDate
            }
        }

        val result = repo.getIncompleteEventsBefore(cutoffDate, "default")

        result.shouldBeEmpty()
    }
})
