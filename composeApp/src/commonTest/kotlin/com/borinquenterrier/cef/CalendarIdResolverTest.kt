package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class CalendarIdResolverTest : FunSpec({
    val syncService = mockk<GoogleCalendarSyncService>()
    val preferencesRepository = mockk<PreferencesRepository>()
    val resolver = CalendarIdResolver(syncService, preferencesRepository)

    test("resolveCalendarId returns non-default ID as-is") {
        val result = resolver.resolveCalendarId("custom-calendar-id")

        result shouldBe "custom-calendar-id"
    }

    test("resolveCalendarId resolves default ID from saved preference") {
        val prefs = StudyPreferences(googleCalendarId = "saved-id")
        coEvery { preferencesRepository.getPreferences() } returns prefs

        val result = resolver.resolveCalendarId("default")

        result shouldBe "saved-id"
        coVerify { preferencesRepository.getPreferences() }
    }

    test("resolveCalendarId finds existing CEF calendar") {
        val prefs = StudyPreferences(googleCalendarId = "", googleCalendarName = "CEF Academic")
        val calendars = listOf(
            RemoteCalendarMetadata(id = "existing-cef-id", name = "CEF Academic"),
            RemoteCalendarMetadata(id = "other-id", name = "Other")
        )
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns calendars

        val result = resolver.resolveCalendarId("default")

        result shouldBe "existing-cef-id"
    }

    test("resolveCalendarId creates CEF calendar if not found") {
        val prefs = StudyPreferences(googleCalendarId = "", googleCalendarName = "CEF Academic")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns emptyList()
        coEvery { syncService.createCalendar("CEF Academic") } returns "new-calendar-id"

        val result = resolver.resolveCalendarId("default")

        result shouldBe "new-calendar-id"
        coVerify { syncService.createCalendar("CEF Academic") }
    }

    test("resolveCalendarId uses custom calendar name from preferences") {
        val prefs = StudyPreferences(googleCalendarId = "", googleCalendarName = "My Custom Calendar")
        coEvery { preferencesRepository.getPreferences() } returns prefs
        coEvery { syncService.listCalendars() } returns emptyList()
        coEvery { syncService.createCalendar("My Custom Calendar") } returns "new-id"

        resolver.resolveCalendarId("default")

        coVerify { syncService.createCalendar("My Custom Calendar") }
    }
})
