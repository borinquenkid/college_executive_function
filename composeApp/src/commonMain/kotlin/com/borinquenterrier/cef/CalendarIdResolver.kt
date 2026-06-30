package com.borinquenterrier.cef

/**
 * Resolves the effective calendar ID to use, finding or creating the CEF Academic calendar as needed.
 */
class CalendarIdResolver(
    private val syncService: GoogleCalendarSyncService,
    private val preferencesRepository: PreferencesPort
) {
    /**
     * Finds the user-configured calendar ID, or creates/finds the default calendar if it doesn't exist.
     * @param calendarId User-requested calendar ID, or "default" to use CEF Academic
     * @return The effective calendar ID to use
     */
    suspend fun resolveCalendarId(calendarId: String): String {
        if (calendarId == "default") {
            return getCEFCalendarId()
        }
        return calendarId
    }

    private suspend fun getCEFCalendarId(): String {
        val prefs = preferencesRepository.getPreferences()
        val targetName = prefs.googleCalendarName.ifEmpty { "CEF Academic" }
        val calendars = syncService.listCalendars()

        // Honor the saved id only if that calendar still exists on Google. Otherwise it was
        // deleted/recreated (a new id), and short-circuiting on the stale id would keep throwing
        // CalendarNotFoundException even though the calendar is right there — so re-resolve by name.
        val savedId = prefs.googleCalendarId
        if (savedId != "default" && savedId.isNotEmpty() && calendars.any { it.id == savedId }) {
            return savedId
        }

        val resolvedId = calendars.find { it.name == targetName }?.id
            ?: syncService.createCalendar(targetName)
        preferencesRepository.savePreferences(prefs.copy(googleCalendarId = resolvedId))
        return resolvedId
    }
}
