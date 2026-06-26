package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

@Serializable
data class StudyPreferences(
    val studyStartHour: Int = 9,
    val studyEndHour: Int = 21,
    val lunchStartHour: Int = 12,
    val lunchEndHour: Int = 13,
    val dinnerStartHour: Int = 17,
    val dinnerEndHour: Int = 19,
    val maxStudyBlockHours: Int = 2,
    val preferredBreakMinutes: Int = 30,
    val shareAnonymousBugReports: Boolean = false,
    val googleCalendarId: String = "default",
    val googleCalendarName: String = "CEF Academic",
    val semesterStart: String? = null,
    val semesterEnd: String? = null
)
