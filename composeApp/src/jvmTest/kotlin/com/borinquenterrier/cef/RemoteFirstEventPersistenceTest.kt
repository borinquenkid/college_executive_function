package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class RemoteFirstEventPersistenceTest : FunSpec({

    lateinit var localRepo: StudentCalendarRepository
    lateinit var remoteRepo: RemoteCalendarRepository
    lateinit var syncGate: SyncGate
    lateinit var logger: Logger
    lateinit var prefMemory: UserPreferenceMemoryRepository
    lateinit var persistence: RemoteFirstEventPersistence

    val date = LocalDate(2026, 9, 1)
    val event = TimeEvent(
        id = "e1", title = "Test", source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS,
        date = date, startTime = LocalTime(9, 0), endTime = LocalTime(10, 0)
    )
    val studyBlock = TimeEvent(
        id = "sb1", title = "Study", source = EventSource.AI_GENERATED,
        category = AcademicCategory.STUDY_BLOCK,
        date = date, startTime = LocalTime(9, 0), endTime = LocalTime(10, 0)
    )

    beforeEach {
        localRepo = mockk(relaxed = true)
        remoteRepo = mockk(relaxed = true)
        syncGate = mockk()
        logger = mockk(relaxed = true)
        prefMemory = mockk(relaxed = true)
        persistence = RemoteFirstEventPersistence(localRepo, remoteRepo, syncGate, logger, prefMemory)
    }

    // ── save ─────────────────────────────────────────────────────────────────

    test("save pushes to remote and marks SYNCED when live") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } just runs

        persistence.save(event, "default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(event, "default") }
        coVerify(exactly = 1) { localRepo.saveEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("save falls back to LOCAL_ONLY and throws RemoteSyncFailedException on network error") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("offline")

        shouldThrow<RemoteSyncFailedException> { persistence.save(event, "default") }

        coVerify(exactly = 1) { localRepo.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("save rethrows CalendarNotFoundException without local fallback") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "gone")

        shouldThrow<CalendarNotFoundException> { persistence.save(event, "default") }

        coVerify(exactly = 0) { localRepo.saveEvent(any(), any()) }
    }

    test("save stores LOCAL_ONLY directly when not live") {
        every { syncGate.isLive() } returns false

        persistence.save(event, "default")

        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
        coVerify(exactly = 1) { localRepo.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    // ── update ───────────────────────────────────────────────────────────────

    test("update pushes to remote and marks SYNCED when live") {
        every { syncGate.isLive() } returns true
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        persistence.update(event, "default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(event, "default") }
        coVerify(exactly = 1) { localRepo.updateEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("update logs MOVE override when study block time changes") {
        every { syncGate.isLive() } returns true
        val moved = studyBlock.copy(startTime = LocalTime(14, 0), endTime = LocalTime(15, 0))
        coEvery { localRepo.getAllEvents(any()) } returns listOf(studyBlock)

        persistence.update(moved, "default")

        coVerify(exactly = 1) { prefMemory.logOverride(OverrideAction.MOVE, studyBlock) }
    }

    test("update falls back to LOCAL_ONLY on network error") {
        every { syncGate.isLive() } returns true
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("offline")

        persistence.update(event, "default")

        coVerify(exactly = 1) { localRepo.updateEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("update rethrows CalendarNotFoundException") {
        every { syncGate.isLive() } returns true
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "gone")

        shouldThrow<CalendarNotFoundException> { persistence.update(event, "default") }
    }

    test("update writes locally only when not live") {
        every { syncGate.isLive() } returns false
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        persistence.update(event, "default")

        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
        coVerify(exactly = 1) { localRepo.updateEvent(event, "default") }
    }

    // ── delete ───────────────────────────────────────────────────────────────

    test("delete soft-deletes locally, pushes remote delete, then hard-deletes when live") {
        every { syncGate.isLive() } returns true
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        persistence.delete("e1", "default")

        coVerify(exactly = 1) { localRepo.deleteEvent("e1", "default") }
        coVerify(exactly = 1) { remoteRepo.deleteEvent("e1", "default") }
        coVerify(exactly = 1) { localRepo.hardDeleteEvent("e1", "default") }
    }

    test("delete logs study block override before soft-delete") {
        every { syncGate.isLive() } returns true
        coEvery { localRepo.getAllEvents(any()) } returns listOf(studyBlock)

        persistence.delete("sb1", "default")

        coVerify(exactly = 1) { prefMemory.logOverride(OverrideAction.DELETE, studyBlock) }
        coVerify(exactly = 1) { localRepo.deleteEvent("sb1", "default") }
    }

    test("delete leaves DELETED_LOCALLY when remote delete fails") {
        every { syncGate.isLive() } returns true
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.deleteEvent(any(), any()) } throws RuntimeException("offline")

        persistence.delete("e1", "default")

        coVerify(exactly = 1) { localRepo.deleteEvent("e1", "default") }
        coVerify(exactly = 0) { localRepo.hardDeleteEvent(any(), any()) }
    }

    test("delete only soft-deletes locally when not live") {
        every { syncGate.isLive() } returns false
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        persistence.delete("e1", "default")

        coVerify(exactly = 1) { localRepo.deleteEvent("e1", "default") }
        coVerify(exactly = 0) { remoteRepo.deleteEvent(any(), any()) }
    }

    // ── retryLocalOnly ───────────────────────────────────────────────────────

    test("retryLocalOnly pushes pending events and marks them SYNCED") {
        every { syncGate.isLive() } returns true
        val pending = event.copy(syncStatus = SyncStatus.LOCAL_ONLY)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns listOf(pending)

        persistence.retryLocalOnly("default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(pending, "default") }
        coVerify(exactly = 1) { localRepo.updateEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("retryLocalOnly skips when not live") {
        every { syncGate.isLive() } returns false

        persistence.retryLocalOnly("default")

        coVerify(exactly = 0) { localRepo.getEventsBySyncStatus(any(), any()) }
    }

    test("retryLocalOnly keeps event LOCAL_ONLY when push fails") {
        every { syncGate.isLive() } returns true
        val pending = event.copy(syncStatus = SyncStatus.LOCAL_ONLY)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns listOf(pending)
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("offline")

        persistence.retryLocalOnly("default")

        coVerify(exactly = 0) { localRepo.updateEvent(any(), any()) }
    }

    // ── reset ────────────────────────────────────────────────────────────────

    test("reset clears local and remote when live") {
        every { syncGate.isLive() } returns true

        persistence.reset("default")

        coVerify(exactly = 1) { localRepo.clearLocalCalendar("default") }
        coVerify(exactly = 1) { remoteRepo.clearCalendar("default") }
    }

    test("reset clears only local when not live") {
        every { syncGate.isLive() } returns false

        persistence.reset("default")

        coVerify(exactly = 1) { localRepo.clearLocalCalendar("default") }
        coVerify(exactly = 0) { remoteRepo.clearCalendar(any()) }
    }

    test("reset succeeds even when remote clear fails") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.clearCalendar(any()) } throws RuntimeException("offline")

        persistence.reset("default")

        coVerify(exactly = 1) { localRepo.clearLocalCalendar("default") }
    }
})
