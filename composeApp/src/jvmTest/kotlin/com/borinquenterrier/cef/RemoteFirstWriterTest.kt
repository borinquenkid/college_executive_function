package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class RemoteFirstWriterTest : FunSpec({

    val event = TimeEvent(
        id = "e1", title = "Exam", source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = LocalDate(2026, 9, 1),
        startTime = LocalTime(9, 0), endTime = LocalTime(10, 0)
    )

    fun setup(live: Boolean): Triple<StudentCalendarRepository, RemoteCalendarRepository, RemoteFirstWriter> {
        val local = mockk<StudentCalendarRepository>(relaxed = true)
        val remote = mockk<RemoteCalendarRepository>(relaxed = true)
        val gate = mockk<SyncGate>().also { every { it.isLive() } returns live }
        return Triple(local, remote, RemoteFirstWriter(local, remote, gate, null))
    }

    // ── save — live ───────────────────────────────────────────────────────────

    test("save pushes to remote and marks SYNCED when live") {
        val (local, remote, writer) = setup(live = true)
        coEvery { remote.saveEvent(any(), any()) } just runs

        writer.save(event, "default")

        coVerify(exactly = 1) { remote.saveEvent(event, "default") }
        coVerify(exactly = 1) { local.saveEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("save falls back to LOCAL_ONLY and throws RemoteSyncFailedException on network failure") {
        val (local, remote, writer) = setup(live = true)
        coEvery { remote.saveEvent(any(), any()) } throws RuntimeException("offline")

        shouldThrow<RemoteSyncFailedException> { writer.save(event, "default") }

        coVerify(exactly = 1) { local.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("save rethrows CalendarNotFoundException without local fallback") {
        val (local, _, writer) = setup(live = true)
        val remote = mockk<RemoteCalendarRepository>()
        coEvery { remote.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "gone")

        val (local2, remote2, writer2) = setup(live = true)
        coEvery { remote2.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "gone")

        shouldThrow<CalendarNotFoundException> { writer2.save(event, "default") }

        coVerify(exactly = 0) { local2.saveEvent(any(), any()) }
    }

    // ── save — offline ────────────────────────────────────────────────────────

    test("save stores LOCAL_ONLY directly when not live") {
        val (local, remote, writer) = setup(live = false)

        writer.save(event, "default")

        coVerify(exactly = 0) { remote.saveEvent(any(), any()) }
        coVerify(exactly = 1) { local.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    // ── update — live ─────────────────────────────────────────────────────────

    test("update pushes to remote and marks SYNCED when live") {
        val (local, remote, writer) = setup(live = true)
        coEvery { remote.saveEvent(any(), any()) } just runs

        writer.update(event, "default")

        coVerify(exactly = 1) { remote.saveEvent(event, "default") }
        coVerify(exactly = 1) { local.updateEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("update marks LOCAL_ONLY on network failure without throwing") {
        val (local, remote, writer) = setup(live = true)
        coEvery { remote.saveEvent(any(), any()) } throws RuntimeException("offline")

        writer.update(event, "default")

        coVerify(exactly = 1) { local.updateEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("update rethrows CalendarNotFoundException") {
        val (_, _, _) = setup(live = true)
        val local2 = mockk<StudentCalendarRepository>(relaxed = true)
        val remote2 = mockk<RemoteCalendarRepository>(relaxed = true)
        val gate2 = mockk<SyncGate>().also { every { it.isLive() } returns true }
        val writer2 = RemoteFirstWriter(local2, remote2, gate2, null)
        coEvery { remote2.saveEvent(any(), any()) } throws CalendarNotFoundException("cal", "gone")

        shouldThrow<CalendarNotFoundException> { writer2.update(event, "default") }
    }

    // ── update — offline ──────────────────────────────────────────────────────

    test("update writes locally only when not live") {
        val (local, remote, writer) = setup(live = false)

        writer.update(event, "default")

        coVerify(exactly = 0) { remote.saveEvent(any(), any()) }
        coVerify(exactly = 1) { local.updateEvent(event, "default") }
    }
})
