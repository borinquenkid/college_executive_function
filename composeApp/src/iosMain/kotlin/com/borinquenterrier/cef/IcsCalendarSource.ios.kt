package com.borinquenterrier.cef

actual class IcsCalendarSource actual constructor(private val icsContent: String) : CalendarInterface {
    actual override suspend fun getEvents(): List<Event> {
        return emptyList()
    }
}
