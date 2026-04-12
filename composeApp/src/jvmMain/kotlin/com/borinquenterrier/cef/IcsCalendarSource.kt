package com.borinquenterrier.cef

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import java.io.StringReader
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import kotlinx.datetime.*

/**
 * JVM-specific implementation of a Calendar Source that reads from an .ics string.
 * This uses iCal4j to parse the structure and map it to CEF Events.
 */
class IcsCalendarSource(private val icsContent: String) : CalendarInterface {

    override suspend fun getEvents(): List<Event> {
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icsContent))
        val vEvents = calendar.getComponents<VEvent>(Component.VEVENT)

        return vEvents.mapNotNull { vEvent ->
            try {
                val summary = vEvent.getProperty<Property>(Property.SUMMARY).map { it.value }.orElse("Untitled Event")
                val startProp = vEvent.getProperty<DtStart<Temporal>>(Property.DTSTART).orElse(null)
                val endProp = vEvent.getProperty<DtEnd<Temporal>>(Property.DTEND).orElse(null)

                if (startProp == null) return@mapNotNull null

                val startDateStr = startProp.value // e.g. 20250902 or 20250902T100000Z
                
                // Determine if it's a DayEvent or TimeEvent
                if (startDateStr.contains("T")) {
                    // TimeEvent
                    // Mapping iCal4j temporal to kotlinx-datetime
                    // Basic parsing for MVP - in a real app use proper timezone handling
                    val year = startDateStr.substring(0, 4).toInt()
                    val month = startDateStr.substring(4, 6).toInt()
                    val day = startDateStr.substring(6, 8).toInt()
                    val hour = startDateStr.substring(9, 11).toInt()
                    val min = startDateStr.substring(11, 13).toInt()

                    val date = LocalDate(year, month, day)
                    val startTime = LocalTime(hour, min)
                    
                    // Default 1 hour duration if end is missing
                    val endTime = if (endProp != null) {
                        val e = endProp.value
                        LocalTime(e.substring(9, 11).toInt(), e.substring(11, 13).toInt())
                    } else {
                        LocalTime(hour + 1, min)
                    }

                    TimeEvent(
                        title = summary,
                        source = EventSource.SCHOOL,
                        date = date,
                        startTime = startTime,
                        endTime = endTime
                    )
                } else {
                    // DayEvent
                    val year = startDateStr.substring(0, 4).toInt()
                    val month = startDateStr.substring(4, 6).toInt()
                    val day = startDateStr.substring(6, 8).toInt()
                    
                    DayEvent(
                        title = summary,
                        source = EventSource.SCHOOL,
                        date = LocalDate(year, month, day)
                    )
                }
            } catch (e: Exception) {
                null // Skip events that fail to parse
            }
        }
    }
}
