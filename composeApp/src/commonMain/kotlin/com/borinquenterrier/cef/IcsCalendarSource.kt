package com.borinquenterrier.cef

expect class IcsCalendarSource(icsContent: String) : CalendarInterface {
    override suspend fun getEvents(): List<Event>
}
