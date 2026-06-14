package com.borinquenterrier.cef

/**
 * Pure, stateless helper that converts raw user-entered strings into a validated
 * [StudyPreferences] object. Extracted from [SettingsScreen] so that the parsing
 * logic can be unit-tested independently of Compose state.
 */
object SettingsPreferencesParser {

    /**
     * Parses raw strings into [StudyPreferences].
     * Any field whose string value cannot be parsed as an [Int] falls back to the
     * corresponding value from [currentPrefs].
     */
    fun parse(
        studyStartStr: String,
        studyEndStr: String,
        lunchStartStr: String,
        lunchEndStr: String,
        dinnerStartStr: String,
        dinnerEndStr: String,
        maxStudyBlockStr: String,
        preferredBreakStr: String,
        shareAnonymousBugReports: Boolean,
        googleCalendarId: String,
        googleCalendarName: String,
        currentPrefs: StudyPreferences = StudyPreferences(),
    ): StudyPreferences = StudyPreferences(
        studyStartHour = studyStartStr.toIntOrNull() ?: currentPrefs.studyStartHour,
        studyEndHour = studyEndStr.toIntOrNull() ?: currentPrefs.studyEndHour,
        lunchStartHour = lunchStartStr.toIntOrNull() ?: currentPrefs.lunchStartHour,
        lunchEndHour = lunchEndStr.toIntOrNull() ?: currentPrefs.lunchEndHour,
        dinnerStartHour = dinnerStartStr.toIntOrNull() ?: currentPrefs.dinnerStartHour,
        dinnerEndHour = dinnerEndStr.toIntOrNull() ?: currentPrefs.dinnerEndHour,
        maxStudyBlockHours = maxStudyBlockStr.toIntOrNull() ?: currentPrefs.maxStudyBlockHours,
        preferredBreakMinutes = preferredBreakStr.toIntOrNull()
            ?: currentPrefs.preferredBreakMinutes,
        shareAnonymousBugReports = shareAnonymousBugReports,
        googleCalendarId = googleCalendarId,
        googleCalendarName = googleCalendarName,
    )
}
