package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class RemoteFirstWriterTest : FunSpec({

    lateinit var localRepo: StudentCalendarRepository
    lateinit var remoteRepo: RemoteCalendarRepository
    lateinit var syncGate: SyncGate
    lateinit var writer: RemoteFirstWriter

    val event = TimeEvent(
        id = "e1", title = "Exam", source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = LocalDate(2026, 9, 1),
        startTime = LocalTime(9, 0), endTime = LocalTime(10, 0)
    )

    beforeEach {
        localRepo = mockk(relaxed = true)
        remoteRepo = mockk(relaxed = true)
        syncGate = mockk()
        writer = RemoteFirstWriter(localRepo, remoteRepo, syncGate, null)
    }

    // ── save — live ───────────────────────────────────────────────────────────

    test("save pushes to remote and marks SYNCED when live") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } just runs

        writer.save(event, "default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(event, "default") }
        coVerify(exactly = 1) { localRepo.saveEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("save preserves all event fields when writing SYNCED") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } just runs

        writer.save(event, "default")

        coVerify {
            localRepo.saveEvent(
                match { it.id == "e1" && it.title == "Exam" && it.syncStatus == SyncStatus.SYNCED },
                "default"
            )
        }
    }

    test("save falls back to LOCAL_ONLY and throws RemoteSyncFailedException on network error") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("timeout")

        shouldThrow<RemoteSyncFailedException> { writer.save(event, "default") }

        coVerify(exactly = 1) { localRepo.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("save wraps the original exception as cause in RemoteSyncFailedException") {
        every { syncGate.isLive() } returns true
        val cause = RuntimeException("DNS failure")
        coEvery { remoteRepo.saveEvent(any(), any()) } throws cause

        val ex = shouldThrow<RemoteSyncFailedException> { writer.save(event, "default") }

        ex.cause shouldBe cause
    }

    test("save rethrows CalendarNotFoundException without saving locally") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "deleted")

        shouldThrow<CalendarNotFoundException> { writer.save(event, "default") }

        coVerify(exactly = 0) { localRepo.saveEvent(any(), any()) }
    }

    test("save writes LOCAL_ONLY directly without touching remote when not live") {
        every { syncGate.isLive() } returns false

        writer.save(event, "default")

        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
        coVerify(exactly = 1) { localRepo.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    // ── update — live ─────────────────────────────────────────────────────────

    test("update pushes to remote and marks SYNCED when live") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } just runs

        writer.update(event, "default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(event, "default") }
        coVerify(exactly = 1) { localRepo.updateEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("update marks LOCAL_ONLY on network error without rethrowing") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("offline")

        writer.update(event, "default")  // must not throw

        coVerify(exactly = 1) { localRepo.updateEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("update does not save a SYNCED copy when network error occurs") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("offline")

        writer.update(event, "default")

        coVerify(exactly = 0) { localRepo.updateEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("update rethrows CalendarNotFoundException without updating locally") {
        every { syncGate.isLive() } returns true
        coEvery { remoteRepo.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "deleted")

        shouldThrow<CalendarNotFoundException> { writer.update(event, "default") }

        coVerify(exactly = 0) { localRepo.updateEvent(any(), any()) }
    }

    test("update writes the original event locally without changing syncStatus when not live") {
        every { syncGate.isLive() } returns false

        writer.update(event, "default")

        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
        coVerify(exactly = 1) { localRepo.updateEvent(event, "default") }
    }
})
