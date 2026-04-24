package com.borinquenterrier.cef

actual class IcsCalendarSource actual constructor(private val icsContent: String) : CalendarInterface {
    actual override suspend fun getEvents(): List<Event> {
        // Basic .ics parsing for Android could be added here later using a KMP library 
        // or a native Android library. For now, we return empty list.
        return emptyList()
    }
}
