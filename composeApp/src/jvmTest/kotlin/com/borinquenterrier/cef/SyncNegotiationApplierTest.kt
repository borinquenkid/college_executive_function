package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class SyncNegotiationApplierTest : FunSpec({

    lateinit var localRepo: StudentCalendarRepository
    lateinit var remoteRepo: RemoteCalendarRepository
    lateinit var logger: Logger
    lateinit var prefMemoryRepo: UserPreferenceMemoryRepository

    // A simple MapSettings-backed settings object reporting run_profile = "local" (live sync)
    val liveSettings = MapSettings().also { it.putString("run_profile", "local") }
    // A test-profile settings (disables live sync)
    val testSettings = MapSettings().also { it.putString("run_profile", "test") }

    fun makeTimeEvent(
        id: String,
        title: String = id,
        category: AcademicCategory = AcademicCategory.REGULAR,
        date: LocalDate = LocalDate(2026, 9, 1),
        start: LocalTime = LocalTime(9, 0),
        end: LocalTime = LocalTime(10, 0),
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        updatedAt: Long = 0L
    ) = TimeEvent(
        id = id, title = title, source = EventSource.AI_GENERATED,
        category = category, date = date,
        startTime = start, endTime = end,
        syncStatus = syncStatus, updatedAt = updatedAt
    )

    beforeEach {
        localRepo       = mockk(relaxed = true)
        remoteRepo      = mockk(relaxed = true)
        logger          = mockk(relaxed = true)
        prefMemoryRepo  = mockk(relaxed = true)
    }

    // ── applyDeletedLocalEvents ───────────────────────────────────────────────

    test("apply hard-deletes IDs listed in deletedLocalIds") {
        val event = makeTimeEvent("exam-1", category = AcademicCategory.FINALS)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(event)

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger, prefMemoryRepo)
        val negotiation = SyncNegotiation(
            proposals          = emptyList(),
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = listOf("exam-1")
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { localRepo.hardDeleteEvent("exam-1", "default") }
    }

    test("apply logs DELETE override when deleted event is a STUDY_BLOCK") {
        val studyBlock = makeTimeEvent("sb-1", category = AcademicCategory.STUDY_BLOCK)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(studyBlock)

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger, prefMemoryRepo)
        val negotiation = SyncNegotiation(
            proposals          = emptyList(),
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = listOf("sb-1")
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { prefMemoryRepo.logOverride(OverrideAction.DELETE, studyBlock) }
        coVerify(exactly = 1) { localRepo.hardDeleteEvent("sb-1", "default") }
    }

    test("apply does NOT log override when deleted event is not a STUDY_BLOCK") {
        val exam = makeTimeEvent("exam-2", category = AcademicCategory.FINALS)
        coEvery { localRepo.getAllEvents(any()) } returns listOf(exam)

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger, prefMemoryRepo)
        val negotiation = SyncNegotiation(
            proposals          = emptyList(),
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = listOf("exam-2")
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 0) { prefMemoryRepo.logOverride(any(), any()) }
    }

    // ── applyRemoteEventsToLocal ──────────────────────────────────────────────

    test("apply upserts all remote events into local repo") {
        val remote = makeTimeEvent("r-1", updatedAt = 100L)
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger)
        val negotiation = SyncNegotiation(
            proposals          = emptyList(),
            remoteEventsToSync = listOf(remote),
            deletedLocalIds    = emptyList()
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { localRepo.updateEvent(match { it.id == "r-1" && it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("apply logs MOVE override when a STUDY_BLOCK is moved remotely") {
        val localSb  = makeTimeEvent("sb-2", category = AcademicCategory.STUDY_BLOCK, updatedAt = 1L,
            date = LocalDate(2026, 9, 1))
        val remoteSb = makeTimeEvent("sb-2", category = AcademicCategory.STUDY_BLOCK, updatedAt = 2L,
            date = LocalDate(2026, 9, 3))  // different date → MOVE
        coEvery { localRepo.getAllEvents(any()) } returns listOf(localSb)

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger, prefMemoryRepo)
        val negotiation = SyncNegotiation(
            proposals          = emptyList(),
            remoteEventsToSync = listOf(remoteSb),
            deletedLocalIds    = emptyList()
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { prefMemoryRepo.logOverride(OverrideAction.MOVE, localSb) }
    }

    // ── applyShiftedStudyBlocks — live sync disabled ──────────────────────────

    test("apply writes shifted study block locally only when run_profile is test") {
        val original = makeTimeEvent("sb-3", date = LocalDate(2026, 9, 1))
        val proposed = makeTimeEvent("sb-3", date = LocalDate(2026, 9, 2))  // different date
        val collider = makeTimeEvent("col-1")
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { localRepo.getSettings()       } returns testSettings

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger)
        val negotiation = SyncNegotiation(
            proposals          = listOf(SyncProposal.StudyBlockShift(original, proposed, collider)),
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = emptyList()
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { localRepo.updateEvent(match { it.id == "sb-3" }, "default") }
        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
    }

    // ── applyShiftedStudyBlocks — live sync enabled ───────────────────────────

    test("apply saves shifted study block to remote and local when run_profile is local") {
        val original = makeTimeEvent("sb-4", date = LocalDate(2026, 9, 1))
        val proposed = makeTimeEvent("sb-4", date = LocalDate(2026, 9, 2))
        val collider = makeTimeEvent("col-2")
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { localRepo.getSettings()       } returns liveSettings

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger)
        val negotiation = SyncNegotiation(
            proposals          = listOf(SyncProposal.StudyBlockShift(original, proposed, collider)),
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = emptyList()
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(match { it.id == "sb-4" }, "default") }
        coVerify(exactly = 1) { localRepo.updateEvent(match { it.id == "sb-4" && it.syncStatus == SyncStatus.SYNCED }, "default") }
    }

    test("apply marks study block LOCAL_ONLY when remote save fails") {
        val original = makeTimeEvent("sb-5", date = LocalDate(2026, 9, 1))
        val proposed = makeTimeEvent("sb-5", date = LocalDate(2026, 9, 2))
        val collider = makeTimeEvent("col-3")
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { localRepo.getSettings()       } returns liveSettings
        coEvery { remoteRepo.saveEvent(any(), any()) } throws RuntimeException("network error")

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger)
        val negotiation = SyncNegotiation(
            proposals          = listOf(SyncProposal.StudyBlockShift(original, proposed, collider)),
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = emptyList()
        )

        applier.apply(negotiation, "default")

        coVerify(exactly = 1) { localRepo.updateEvent(match { it.id == "sb-5" && it.syncStatus == SyncStatus.LOCAL_ONLY }, "default") }
    }

    test("apply skips proposal when proposed and original events are identical") {
        val event    = makeTimeEvent("sb-6")
        val collider = makeTimeEvent("col-4")
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()

        val applier = SyncNegotiationApplier(localRepo, remoteRepo, logger)
        val negotiation = SyncNegotiation(
            proposals          = listOf(SyncProposal.StudyBlockShift(event, event, collider)), // same!
            remoteEventsToSync = emptyList(),
            deletedLocalIds    = emptyList()
        )

        applier.apply(negotiation, "default")

        // No save or update should be triggered for the proposal
        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
        coVerify(exactly = 0) { localRepo.updateEvent(any(), any()) }
    }
})
