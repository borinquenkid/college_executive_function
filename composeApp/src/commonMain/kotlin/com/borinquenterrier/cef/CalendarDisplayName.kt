package com.borinquenterrier.cef

object CalendarDisplayName {

    private const val DEFAULT_ID = "default"
    private const val DEFAULT_LABEL = "CEF Academic (Default)"

    fun resolve(calendarId: String, calendarName: String): String =
        if (calendarId == DEFAULT_ID) DEFAULT_LABEL else calendarName
}
