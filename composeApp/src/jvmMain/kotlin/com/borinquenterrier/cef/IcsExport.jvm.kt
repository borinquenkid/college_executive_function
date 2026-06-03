package com.borinquenterrier.cef

actual fun generateIcsString(events: List<Event>): String {
    val calendar = ICalGenerator.buildAcademicCalendar(events)
    return ICalGenerator.calendarToString(calendar)
}

actual fun writeIcsFile(content: String): String {
    val userHome = System.getProperty("user.home")
    val downloadsDir = java.io.File(userHome, "Downloads")
    if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
    }
    val file = java.io.File(downloadsDir, "academic_calendar.ics")
    file.writeText(content)
    return file.absolutePath
}
