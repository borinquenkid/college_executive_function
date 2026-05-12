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
actual class IcsCalendarSource actual constructor(private val icsContent: String) {

    actual suspend fun readSource(): List<SourceFragment> {
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icsContent))
        val vEvents = calendar.getComponents<VEvent>(Component.VEVENT)

        return vEvents.map { vEvent ->
            SourceFragment(
                text = vEvent.toString().trim(),
                type = SourceType.CALENDAR
            )
        }
    }
}
