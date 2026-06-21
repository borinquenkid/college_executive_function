package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventDeleterTest : FunSpec({

    val date = LocalDate(2026, 9, 1)

    fun makeDeleter(live: Boolean): Triple<StudentCalendarRepository, RemoteCalendarRepository, EventDeleter> {
        val local = mockk<StudentCalendarRepository>(relaxed = true)
        val remote = mockk<RemoteCalendarRepository>(relaxed = true)
        val gate = mockk<SyncGate>().also { every { it.isLive() } returns live }
        val overrideLogger = mockk<StudyBlockOverrideLogger>(relaxed = true)
        return Triple(local, remote, EventDeleter(local, remote, gate, overrideLogger, null))
    }

    test("delete soft-deletes locally, then hard-deletes after successful remote delete when live") {
        val (local, remote, deleter) = makeDeleter(live = true)

        deleter.delete("e1", "default")

        coVerify(exactly = 1) { local.deleteEvent("e1", "default") }
        coVerify(exactly = 1) { remote.deleteEvent("e1", "default") }
        coVerify(exactly = 1) { local.hardDeleteEvent("e1", "default") }
    }

    test("delete stays DELETED_LOCALLY when remote delete fails") {
        val (local, remote, deleter) = makeDeleter(live = true)
        coEvery { remote.deleteEvent(any(), any()) } throws RuntimeException("offline")

        deleter.delete("e1", "default")

        coVerify(exactly = 1) { local.deleteEvent("e1", "default") }
        coVerify(exactly = 0) { local.hardDeleteEvent(any(), any()) }
    }

    test("delete only soft-deletes locally when not live") {
        val (local, remote, deleter) = makeDeleter(live = false)

        deleter.delete("e1", "default")

        coVerify(exactly = 1) { local.deleteEvent("e1", "default") }
        coVerify(exactly = 0) { remote.deleteEvent(any(), any()) }
        coVerify(exactly = 0) { local.hardDeleteEvent(any(), any()) }
    }

    test("delete calls overrideLogger.checkDelete before soft-delete") {
        val local = mockk<StudentCalendarRepository>(relaxed = true)
        val remote = mockk<RemoteCalendarRepository>(relaxed = true)
        val gate = mockk<SyncGate>().also { every { it.isLive() } returns false }
        val overrideLogger = mockk<StudyBlockOverrideLogger>(relaxed = true)
        val deleter = EventDeleter(local, remote, gate, overrideLogger, null)

        deleter.delete("sb1", "default")

        coVerify(exactly = 1) { overrideLogger.checkDelete("sb1", "default") }
        coVerify(exactly = 1) { local.deleteEvent("sb1", "default") }
    }
})
