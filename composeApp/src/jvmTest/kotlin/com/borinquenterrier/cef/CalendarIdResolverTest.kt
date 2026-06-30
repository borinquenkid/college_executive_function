package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

class CalendarIdResolverTest : FunSpec({
    val syncService = mockk<GoogleCalendarSyncService>()
    val preferencesRepository = mockk<PreferencesRepository>()
    val resolver = CalendarIdResolver(syncService, preferencesRepository)

    beforeEach { clearMocks(syncService, preferencesRepository) }

    test("resolveCalendarId returns provided ID when not 'default'") {
        val result = resolver.resolveCalendarId("user-calendar-id")

        result shouldBe "user-calendar-id"
    }

    test("resolveCalendarId honors the saved calendar ID when it still exists on Google") {
        val prefs = StudyPreferences(googleCalendarId = "saved-calendar-id", googleCalendarName = "My Calendar")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(id = "saved-calendar-id", name = "My Calendar")
        )

        runBlocking {
            resolver.resolveCalendarId("default") shouldBe "saved-calendar-id"
        }

        coVerify(exactly = 0) { syncService.createCalendar(any()) }
        coVerify(exactly = 0) { preferencesRepository.savePreferences(any()) }
    }

    test("resolveCalendarId re-resolves by name when the saved ID no longer exists (deleted/recreated)") {
        // The stored id points at a deleted calendar; a CEF Academic calendar exists under a new id.
        val prefs = StudyPreferences(googleCalendarId = "stale-deleted-id", googleCalendarName = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(
            RemoteCalendarMetadata(id = "new-cef-id", name = "CEF Academic")
        )
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking {
            resolver.resolveCalendarId("default") shouldBe "new-cef-id"
        }

        // Reattaches to the existing calendar and persists the corrected id — no duplicate created.
        coVerify(exactly = 1) {
            preferencesRepository.savePreferences(match { it.googleCalendarId == "new-cef-id" })
        }
        coVerify(exactly = 0) { syncService.createCalendar(any()) }
    }

    test("resolveCalendarId creates the calendar when the saved ID is stale and no calendar matches the name") {
        val prefs = StudyPreferences(googleCalendarId = "stale-deleted-id", googleCalendarName = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns emptyList()
        coEvery { syncService.createCalendar("CEF Academic") } returns "fresh-id"
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking {
            resolver.resolveCalendarId("default") shouldBe "fresh-id"
        }
    }

    test("resolveCalendarId finds existing CEF calendar by name and returns its ID") {
        val prefs = StudyPreferences(googleCalendarId = "default", googleCalendarName = "CEF Academic")
        val existingCal = RemoteCalendarMetadata(id = "existing-cal-id", name = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(existingCal)
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "existing-cal-id"
        }
    }

    test("resolveCalendarId saves found calendar ID to preferences to prevent duplicate creation") {
        val prefs = StudyPreferences(googleCalendarId = "default", googleCalendarName = "CEF Academic")
        val existingCal = RemoteCalendarMetadata(id = "existing-cal-id", name = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(existingCal)
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking { resolver.resolveCalendarId("default") }

        coVerify(exactly = 1) {
            preferencesRepository.savePreferences(match { it.googleCalendarId == "existing-cal-id" })
        }
        coVerify(exactly = 0) { syncService.createCalendar(any()) }
    }

    test("resolveCalendarId creates calendar if not found and returns the new ID") {
        val prefs = StudyPreferences(googleCalendarId = "default", googleCalendarName = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns emptyList()
        coEvery { syncService.createCalendar("CEF Academic") } returns "newly-created-id"
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "newly-created-id"
        }
    }

    test("resolveCalendarId saves newly created calendar ID to preferences to prevent duplicate creation") {
        val prefs = StudyPreferences(googleCalendarId = "default", googleCalendarName = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns emptyList()
        coEvery { syncService.createCalendar("CEF Academic") } returns "newly-created-id"
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking { resolver.resolveCalendarId("default") }

        coVerify(exactly = 1) {
            preferencesRepository.savePreferences(match { it.googleCalendarId == "newly-created-id" })
        }
    }

    test("resolveCalendarId uses default calendar name when preference is empty") {
        val prefs = StudyPreferences(googleCalendarId = "default", googleCalendarName = "")
        val defaultCal = RemoteCalendarMetadata(id = "default-cal-id", name = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(defaultCal)
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "default-cal-id"
        }
    }

    test("resolveCalendarId skips non-matching calendars during search") {
        val prefs = StudyPreferences(googleCalendarId = "default", googleCalendarName = "Target Calendar")
        val otherCal = RemoteCalendarMetadata(id = "other-id", name = "Other Calendar")
        val targetCal = RemoteCalendarMetadata(id = "target-id", name = "Target Calendar")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(otherCal, targetCal)
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "target-id"
        }
    }

    test("resolveCalendarId preserves all other preference fields when saving calendar ID") {
        val prefs = StudyPreferences(
            googleCalendarId = "default",
            googleCalendarName = "CEF Academic",
            studyStartHour = 8,
            studyEndHour = 22,
            maxStudyBlockHours = 3
        )
        val existingCal = RemoteCalendarMetadata(id = "cal-id", name = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(existingCal)
        coEvery { preferencesRepository.savePreferences(any()) } returns Unit

        runBlocking { resolver.resolveCalendarId("default") }

        coVerify(exactly = 1) {
            preferencesRepository.savePreferences(match {
                it.googleCalendarId == "cal-id" &&
                    it.studyStartHour == 8 &&
                    it.studyEndHour == 22 &&
                    it.maxStudyBlockHours == 3
            })
        }
    }
})
