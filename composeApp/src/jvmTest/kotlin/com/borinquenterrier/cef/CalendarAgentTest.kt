package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.russhwolf.settings.MapSettings

class CalendarAgentTest : FunSpec({

    val date = LocalDate(2026, 6, 7)

    val timeEvent = TimeEvent(
        id = "event-1",
        title = "Mock Time Event",
        source = EventSource.CLASS,
        date = date,
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0),
        syncStatus = SyncStatus.SYNCED,
        category = AcademicCategory.CLASS
    )

    val studyBlockEvent = TimeEvent(
        id = "event-2",
        title = "Study Block",
        source = EventSource.AI_GENERATED,
        date = date,
        startTime = LocalTime(11, 0),
        endTime = LocalTime(12, 0),
        syncStatus = SyncStatus.SYNCED,
        category = AcademicCategory.STUDY_BLOCK
    )

    val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
    val remoteRepo = mockk<RemoteCalendarRepository>(relaxed = true)
    val logger = mockk<Logger>(relaxed = true)
    val userPreferenceMemoryRepository = mockk<UserPreferenceMemoryRepository>(relaxed = true)
    val preferencesRepository = mockk<PreferencesRepository>(relaxed = true)

    val calendarAgent = CalendarAgent(
        localRepo = localRepo,
        remoteRepo = remoteRepo,
        logger = logger,
        userPreferenceMemoryRepository = userPreferenceMemoryRepository,
        preferencesRepository = preferencesRepository
    )

    beforeEach {
        clearAllMocks()
    }

    test("getEvents should delegate to localRepo") {
        val mockEvents = listOf(timeEvent)
        coEvery { localRepo.getAllEvents("default") } returns mockEvents

        val result = calendarAgent.getEvents("default")

        result shouldBe mockEvents
        coVerify(exactly = 1) { localRepo.getAllEvents("default") }
    }

    test("saveEvent with local run profile should save to remote and local as SYNCED") {
        val mockSettings = MapSettings()
        mockSettings.putString("run_profile", "local")
        coEvery { localRepo.getSettings() } returns mockSettings
        coEvery { remoteRepo.saveEvent(any(), any()) } just runs
        coEvery { localRepo.saveEvent(any(), any()) } just runs

        calendarAgent.saveEvent(timeEvent, "default")

        coVerify(exactly = 1) { remoteRepo.saveEvent(timeEvent, "default") }
        coVerify(exactly = 1) {
            localRepo.saveEvent(match { it.syncStatus == SyncStatus.SYNCED }, "default")
        }
    }

    test("saveEvent with test run profile should skip remote and save locally as LOCAL_ONLY") {
        val mockSettings = MapSettings()
        mockSettings.putString("run_profile", "test")
        coEvery { localRepo.getSettings() } returns mockSettings
        coEvery { localRepo.saveEvent(any(), any()) } just runs

        calendarAgent.saveEvent(timeEvent, "default")

        coVerify(exactly = 0) { remoteRepo.saveEvent(any(), any()) }
        coVerify(exactly = 1) {
            localRepo.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default")
        }
    }

    test("saveEvent should throw exception if remote repo fails under local profile") {
        val mockSettings = MapSettings()
        mockSettings.putString("run_profile", "local")
        coEvery { localRepo.getSettings() } returns mockSettings
        coEvery { remoteRepo.saveEvent(any(), any()) } throws Exception("Remote failure")

        shouldThrow<Exception> {
            calendarAgent.saveEvent(timeEvent, "default")
        }

        coVerify(exactly = 0) { localRepo.saveEvent(any(), any()) }
    }

    test("saveEventLocally should save event to local repo as LOCAL_ONLY") {
        coEvery { localRepo.saveEvent(any(), any()) } just runs

        calendarAgent.saveEventLocally(timeEvent, "default")

        coVerify(exactly = 1) {
            localRepo.saveEvent(match { it.syncStatus == SyncStatus.LOCAL_ONLY }, "default")
        }
    }

    test("updateEvent with move should log override and save to remote and local under local profile") {
        val mockSettings = MapSettings()
        mockSettings.putString("run_profile", "local")
        coEvery { localRepo.getSettings() } returns mockSettings
        coEvery { localRepo.getAllEvents("default") } returns listOf(studyBlockEvent)
        coEvery { remoteRepo.saveEvent(any(), any()) } just runs
        coEvery { localRepo.updateEvent(any(), any()) } just runs

        val movedStudyBlock = studyBlockEvent.copy(
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0)
        )

        calendarAgent.updateEvent(movedStudyBlock, "default")

        coVerify(exactly = 1) { userPreferenceMemoryRepository.logOverride(OverrideAction.MOVE, studyBlockEvent) }
        coVerify(exactly = 1) { remoteRepo.saveEvent(movedStudyBlock, "default") }
        coVerify(exactly = 1) {
            localRepo.updateEvent(match { it.id == "event-2" && it.syncStatus == SyncStatus.SYNCED }, "default")
        }
    }

    test("deleteEvent should log override, delete locally, attempt remote delete, and hard delete local on remote success") {
        coEvery { localRepo.getAllEvents("default") } returns listOf(studyBlockEvent)
        coEvery { localRepo.deleteEvent(any(), any()) } just runs
        coEvery { remoteRepo.deleteEvent(any(), any()) } just runs
        coEvery { localRepo.hardDeleteEvent(any(), any()) } just runs

        calendarAgent.deleteEvent("event-2", "default")

        coVerify(exactly = 1) { userPreferenceMemoryRepository.logOverride(OverrideAction.DELETE, studyBlockEvent) }
        coVerify(exactly = 1) { localRepo.deleteEvent("event-2", "default") }
        coVerify(exactly = 1) { remoteRepo.deleteEvent("event-2", "default") }
        coVerify(exactly = 1) { localRepo.hardDeleteEvent("event-2", "default") }
    }

    test("deleteEvent should leave soft deleted locally if remote delete fails") {
        coEvery { localRepo.getAllEvents("default") } returns listOf(studyBlockEvent)
        coEvery { localRepo.deleteEvent(any(), any()) } just runs
        coEvery { remoteRepo.deleteEvent(any(), any()) } throws Exception("Remote offline")

        calendarAgent.deleteEvent("event-2", "default")

        coVerify(exactly = 1) { localRepo.deleteEvent("event-2", "default") }
        coVerify(exactly = 1) { remoteRepo.deleteEvent("event-2", "default") }
        coVerify(exactly = 0) { localRepo.hardDeleteEvent("event-2", "default") }
    }

    test("checkSyncProposals should handle pushLocalChanges and detect deleted/conflicting events") {
        // Mock pushLocalChanges details
        val deletedLocalEvent = timeEvent.copy(id = "deleted-1", syncStatus = SyncStatus.DELETED_LOCALLY)
        val localOnlyEvent = timeEvent.copy(id = "local-only", syncStatus = SyncStatus.LOCAL_ONLY)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY, "default") } returns listOf(deletedLocalEvent)
        coEvery { localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, "default") } returns listOf(localOnlyEvent)

        // Mock events for checkSyncProposals main logic
        val conflictingLocal = timeEvent.copy(id = "conflict-1", title = "Local Title", syncStatus = SyncStatus.SYNCED, updatedAt = 100L)
        val conflictingRemote = timeEvent.copy(id = "conflict-1", title = "Remote Title", syncStatus = SyncStatus.SYNCED, updatedAt = 200L)
        coEvery { localRepo.getAllEvents("default") } returns listOf(conflictingLocal)
        coEvery { remoteRepo.getAllEvents("default") } returns listOf(conflictingRemote)

        val negotiation = calendarAgent.checkSyncProposals("default")

        // Check remote deletion call and local creation call from pushLocalChanges
        coVerify(exactly = 1) { remoteRepo.deleteEvent("deleted-1", "default") }
        coVerify(exactly = 1) { localRepo.hardDeleteEvent("deleted-1", "default") }
        coVerify(exactly = 1) { remoteRepo.saveEvent(localOnlyEvent, "default") }
        coVerify(exactly = 1) { localRepo.hardDeleteEvent("local-only", "default") }

        // Check negotiation output
        negotiation.proposals shouldHaveSize 1
        val proposal = negotiation.proposals.first()
        proposal.shouldBeInstanceOf<SyncProposal.DirectConflict>()
        val direct = proposal as SyncProposal.DirectConflict
        direct.localEvent.title shouldBe "Local Title"
        direct.remoteEvent.title shouldBe "Remote Title"
        negotiation.deletedLocalIds shouldBe emptyList()
    }

    test("checkSyncProposals should shift colliding study blocks") {
        val existingClass = TimeEvent(
            id = "class-1",
            title = "Existing Class",
            source = EventSource.CLASS,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0),
            syncStatus = SyncStatus.SYNCED,
            category = AcademicCategory.CLASS
        )
        val localStudyBlock = TimeEvent(
            id = "study-1",
            title = "Colliding Study Block",
            source = EventSource.AI_GENERATED,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0),
            syncStatus = SyncStatus.LOCAL_ONLY,
            category = AcademicCategory.STUDY_BLOCK
        )

        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()
        coEvery { localRepo.getAllEvents("default") } returns listOf(existingClass, localStudyBlock)
        coEvery { remoteRepo.getAllEvents("default") } returns listOf(existingClass)

        // Mock preferences & user preference memory repo
        val prefs = StudyPreferences(
            studyStartHour = 8,
            studyEndHour = 20,
            lunchStartHour = 12,
            lunchEndHour = 13,
            dinnerStartHour = 18,
            dinnerEndHour = 19
        )
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { userPreferenceMemoryRepository.getDerivedConstraints() } returns emptyList()

        val negotiation = calendarAgent.checkSyncProposals("default")

        negotiation.proposals shouldHaveSize 1
        val proposal = negotiation.proposals.first()
        proposal.shouldBeInstanceOf<SyncProposal.StudyBlockShift>()
        val shift = proposal as SyncProposal.StudyBlockShift
        shift.originalEvent.id shouldBe "study-1"
        // Shifted event must not overlap class-1 (9:00 - 10:00)
        shift.proposedEvent.overlaps(existingClass) shouldBe false
    }

    test("applySyncNegotiation should perform local deletion and upserts remote events") {
        val localEvent = studyBlockEvent.copy(id = "deleted-remote-id", syncStatus = SyncStatus.SYNCED)
        coEvery { localRepo.getAllEvents("default") } returns listOf(localEvent)
        coEvery { localRepo.hardDeleteEvent(any(), any()) } just runs
        coEvery { localRepo.updateEvent(any(), any()) } just runs

        val remoteEvent = timeEvent.copy(id = "new-remote", syncStatus = SyncStatus.SYNCED)
        val negotiation = SyncNegotiation(
            proposals = emptyList(),
            remoteEventsToSync = listOf(remoteEvent),
            deletedLocalIds = listOf("deleted-remote-id")
        )

        calendarAgent.applySyncNegotiation(negotiation, "default")

        coVerify(exactly = 1) { userPreferenceMemoryRepository.logOverride(OverrideAction.DELETE, localEvent) }
        coVerify(exactly = 1) { localRepo.hardDeleteEvent("deleted-remote-id", "default") }
        coVerify(exactly = 1) {
            localRepo.updateEvent(match { it.id == "new-remote" && it.syncStatus == SyncStatus.SYNCED }, "default")
        }
    }

    test("getIncompleteEventsBefore should delegate to localRepo") {
        coEvery { localRepo.getIncompleteEventsBefore(date, "default") } returns listOf(studyBlockEvent)

        val result = calendarAgent.getIncompleteEventsBefore(date, "default")

        result shouldHaveSize 1
        result.first().id shouldBe "event-2"
        coVerify(exactly = 1) { localRepo.getIncompleteEventsBefore(date, "default") }
    }

    test("synchronize should call checkSyncProposals and applySyncNegotiation") {
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()
        coEvery { localRepo.getAllEvents(any()) } returns emptyList()
        coEvery { remoteRepo.getAllEvents(any()) } returns emptyList()

        calendarAgent.synchronize("default")

        coVerify(exactly = 1) { remoteRepo.getAllEvents("default") }
        coVerify(exactly = 2) { localRepo.getAllEvents("default") }
    }

    test("synchronize should throw exception if checkSyncProposals fails") {
        coEvery { localRepo.getEventsBySyncStatus(any(), any()) } returns emptyList()
        coEvery { localRepo.getAllEvents(any()) } throws Exception("Sync failed")

        shouldThrow<Exception> {
            calendarAgent.synchronize("default")
        }
    }
})
