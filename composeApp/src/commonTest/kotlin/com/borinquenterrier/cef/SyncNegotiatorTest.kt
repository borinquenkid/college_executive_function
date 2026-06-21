package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class SyncNegotiatorTest : FunSpec({

    fun day(
        title: String,
        date: LocalDate = LocalDate(2025, 10, 1),
        id: String? = title,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        updatedAt: Long = 1000L
    ) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date,
        id = id,
        syncStatus = syncStatus,
        updatedAt = updatedAt
    )

    // ── buildNegotiation — offline handling ─────────────────────────────────

    test("buildNegotiation returns empty negotiation when remote throws (offline)") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(day("HW1"))
        coEvery { remoteRepo.getAllEvents(any()) } throws RuntimeException("No network")

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        val result = negotiator.buildNegotiation("default")

        result.proposals.shouldBeEmpty()
        result.remoteEventsToSync.shouldBeEmpty()
        result.deletedLocalIds.shouldBeEmpty()
    }

    test("buildNegotiation rethrows CalendarNotFoundException from remote") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(day("HW1"))
        coEvery { remoteRepo.getAllEvents(any()) } throws CalendarNotFoundException("default", "Calendar was deleted")

        val negotiator = SyncNegotiator(localRepo, remoteRepo)

        shouldThrow<CalendarNotFoundException> {
            negotiator.buildNegotiation("default")
        }
    }

    // ── buildNegotiation — no changes ───────────────────────────────────────

    test("buildNegotiation returns empty proposals when local and remote are identical") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val event = day("HW1")
        coEvery { localRepo.getAllEvents(any()) } returns listOf(event)
        coEvery { remoteRepo.getAllEvents(any()) } returns listOf(event)
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        val result = negotiator.buildNegotiation("default")

        result.proposals.shouldBeEmpty()
        result.deletedLocalIds.shouldBeEmpty()
    }

    // ── buildNegotiation — remote addition ──────────────────────────────────

    test("buildNegotiation includes new remote event in remoteEventsToSync") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.getAllEvents(any()) } returns listOf(day("RemoteNew"))
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        val result = negotiator.buildNegotiation("default")

        result.remoteEventsToSync shouldHaveSize 1
    }

    // ── buildNegotiation — direct conflict ──────────────────────────────────

    test("buildNegotiation emits DirectConflict when same id has different title") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val local = day("Old Title", id = "event1", updatedAt = 1000L)
        val remote = day("New Title", id = "event1", updatedAt = 2000L)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(local)
        coEvery { remoteRepo.getAllEvents(any()) } returns listOf(remote)
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        val result = negotiator.buildNegotiation("default")

        val conflicts = result.proposals.filterIsInstance<SyncProposal.DirectConflict>()
        conflicts shouldHaveSize 1
        conflicts[0].localEvent.title shouldBe "Old Title"
        conflicts[0].remoteEvent.title shouldBe "New Title"
    }

    // ── buildNegotiation — deleted on remote ────────────────────────────────

    test("buildNegotiation detects locally-synced event deleted on remote") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val local = day("HW1", id = "hw1", syncStatus = SyncStatus.SYNCED)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(local)
        coEvery { remoteRepo.getAllEvents(any()) } returns emptyList()
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        val result = negotiator.buildNegotiation("default")

        result.deletedLocalIds shouldBe listOf("hw1")
    }

    // ── pushLocalChanges — deletions ────────────────────────────────────────

    test("pushLocalChanges deletes DELETED_LOCALLY events from remote and hard-deletes locally") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val deleted = day("HW1", id = "hw1", syncStatus = SyncStatus.DELETED_LOCALLY)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY, any()) } returns listOf(deleted)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns emptyList()
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.getAllEvents(any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        negotiator.buildNegotiation("default")

        coVerify { remoteRepo.deleteEvent("hw1", "default") }
        coVerify { localRepo.hardDeleteEvent("hw1", "default") }
    }

    test("pushLocalChanges keeps DELETED_LOCALLY event when remote delete fails (network error)") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val deleted = day("HW1", id = "hw1", syncStatus = SyncStatus.DELETED_LOCALLY)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY, any()) } returns listOf(deleted)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns emptyList()
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.deleteEvent(any(), any()) } throws RuntimeException("offline")
        coEvery { remoteRepo.getAllEvents(any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        negotiator.buildNegotiation("default")

        // Hard delete should NOT have been called since remote delete failed
        coVerify(exactly = 0) { localRepo.hardDeleteEvent(any(), any()) }
    }

    // ── pushLocalChanges — creations ────────────────────────────────────────

    test("pushLocalChanges pushes LOCAL_ONLY events to remote") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val local = day("New HW", id = "newhw", syncStatus = SyncStatus.LOCAL_ONLY)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY, any()) } returns emptyList()
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns listOf(local)
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.getAllEvents(any()) } returns emptyList()

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        negotiator.buildNegotiation("default")

        coVerify { remoteRepo.saveEvent(local, "default") }
    }

    // ── duplicate proposals (regression: DB can have two copies of same study block) ──

    test("buildNegotiation does not produce duplicate proposals for study blocks with same title and date") {
        val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
        val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val date = LocalDate(2026, 9, 15)
        // Two study blocks with same title+date but different IDs — a known duplicate scenario
        val block1 = TimeEvent(
            id = "study-id-1",
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = kotlinx.datetime.LocalTime(9, 0),
            endTime = kotlinx.datetime.LocalTime(10, 0),
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val block2 = TimeEvent(
            id = "study-id-2",
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = kotlinx.datetime.LocalTime(9, 0),
            endTime = kotlinx.datetime.LocalTime(10, 0),
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        // A class event that will collide with the study blocks
        val classEvent = TimeEvent(
            id = "class-id",
            title = "Math 101",
            source = EventSource.CLASS,
            category = AcademicCategory.CLASS,
            date = date,
            startTime = kotlinx.datetime.LocalTime(9, 0),
            endTime = kotlinx.datetime.LocalTime(10, 0),
            syncStatus = SyncStatus.SYNCED
        )
        coEvery { localRepo.getAllEvents(any()) } returns listOf(block1, block2)
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()
        coEvery { remoteRepo.getAllEvents(any()) } returns listOf(classEvent)

        val negotiator = SyncNegotiator(localRepo, remoteRepo)
        val result = negotiator.buildNegotiation("default")

        val shifts = result.proposals.filterIsInstance<SyncProposal.StudyBlockShift>()
        // Even though two identical study blocks exist, proposals must be deduped by title+date
        val uniqueTitleDates = shifts.map { Pair(it.originalEvent.title, it.originalEvent.date) }.toSet()
        uniqueTitleDates.size shouldBe shifts.size
    }
})
