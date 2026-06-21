package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class LocalOnlyRetrierTest : FunSpec({

    val date = LocalDate(2026, 9, 1)

    val pending = TimeEvent(
        id = "p1", title = "Pending", source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date, startTime = LocalTime(9, 0), endTime = LocalTime(10, 0),
        syncStatus = SyncStatus.LOCAL_ONLY
    )

    fun makeRetrier(live: Boolean): Triple<StudentCalendarRepository, RemoteCalendarRepository, LocalOnlyRetrier> {
        val local = mockk<StudentCalendarRepository>(relaxed = true)
        val remote = mockk<RemoteCalendarRepository>(relaxed = true)
        val gate = mockk<SyncGate>().also { every { it.isLive() } returns live }
        return Triple(local, remote, LocalOnlyRetrier(local, remote, gate, null))
    }

    test("retry pushes LOCAL_ONLY events and marks them SYNCED when live") {
        val (local, remote, retrier) = makeRetrier(live = true)
        coEvery { local.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns listOf(pending)
        coEvery { remote.saveEvent(any(), any()) } just runs

        retrier.retry("default")

        coVerify(exactly = 1) { remote.saveEvent(pending, "default") }
        coVerify(exactly = 1) { local.updateEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("retry skips when not live") {
        val (local, _, retrier) = makeRetrier(live = false)

        retrier.retry("default")

        coVerify(exactly = 0) { local.getEventsBySyncStatus(any(), any()) }
    }

    test("retry keeps event LOCAL_ONLY when push fails") {
        val (local, remote, retrier) = makeRetrier(live = true)
        coEvery { local.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns listOf(pending)
        coEvery { remote.saveEvent(any(), any()) } throws RuntimeException("offline")

        retrier.retry("default")

        coVerify(exactly = 0) { local.updateEvent(any(), any()) }
    }

    test("retry processes all pending events independently") {
        val (local, remote, retrier) = makeRetrier(live = true)
        val p2 = pending.copy(id = "p2", title = "Pending 2")
        coEvery { local.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, any()) } returns listOf(pending, p2)
        coEvery { remote.saveEvent(match { it.id == "p1" }, any()) } just runs
        coEvery { remote.saveEvent(match { it.id == "p2" }, any()) } throws RuntimeException("offline")

        retrier.retry("default")

        coVerify(exactly = 1) { local.updateEvent(match { it.id == "p1" && it.syncStatus == SyncStatus.SYNCED }, "default") }
        coVerify(exactly = 0) { local.updateEvent(match { it.id == "p2" }, any()) }
    }
})
