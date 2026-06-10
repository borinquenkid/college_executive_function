package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

class CalendarIdResolverTest : FunSpec({
    val syncService = mockk<GoogleCalendarSyncService>()
    val preferencesRepository = mockk<PreferencesRepository>()
    val resolver = CalendarIdResolver(syncService, preferencesRepository)

    test("resolveCalendarId returns provided ID when not 'default'") {
        val result = resolver.resolveCalendarId("user-calendar-id")

        result shouldBe "user-calendar-id"
    }

    test("resolveCalendarId resolves 'default' to saved calendar ID") {
        val prefs = mockk<StudyPreferences> {
            every { googleCalendarId } returns "saved-calendar-id"
            every { googleCalendarName } returns "My Calendar"
        }
        coEvery { preferencesRepository.getPreferences() } returns prefs

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "saved-calendar-id"
        }
    }

    test("resolveCalendarId finds existing CEF calendar by name") {
        val prefs = mockk<StudyPreferences> {
            every { googleCalendarId } returns "default"
            every { googleCalendarName } returns "CEF Academic"
        }
        val existingCal = mockk<RemoteCalendarMetadata> {
            every { id } returns "existing-cal-id"
            every { name } returns "CEF Academic"
        }
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(existingCal)

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "existing-cal-id"
        }
    }

    test("resolveCalendarId creates calendar if not found") {
        val prefs = mockk<StudyPreferences> {
            every { googleCalendarId } returns "default"
            every { googleCalendarName } returns "CEF Academic"
        }
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns emptyList()
        coEvery { syncService.createCalendar("CEF Academic") } returns "newly-created-id"

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "newly-created-id"
        }
    }

    test("resolveCalendarId uses default calendar name when preference is empty") {
        val prefs = mockk<StudyPreferences> {
            every { googleCalendarId } returns "default"
            every { googleCalendarName } returns ""
        }
        val defaultCal = mockk<RemoteCalendarMetadata> {
            every { id } returns "default-cal-id"
            every { name } returns "CEF Academic"
        }
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(defaultCal)

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "default-cal-id"
        }
    }

    test("resolveCalendarId skips non-matching calendars during search") {
        val prefs = mockk<StudyPreferences> {
            every { googleCalendarId } returns "default"
            every { googleCalendarName } returns "Target Calendar"
        }
        val otherCal = mockk<RemoteCalendarMetadata> {
            every { id } returns "other-id"
            every { name } returns "Other Calendar"
        }
        val targetCal = mockk<RemoteCalendarMetadata> {
            every { id } returns "target-id"
            every { name } returns "Target Calendar"
        }
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns listOf(otherCal, targetCal)

        runBlocking {
            val result = resolver.resolveCalendarId("default")

            result shouldBe "target-id"
        }
    }
})
