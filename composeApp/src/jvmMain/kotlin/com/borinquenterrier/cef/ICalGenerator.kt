package com.borinquenterrier.cef

import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyContainer
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.ComponentContainer
import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.temporal.Temporal
import java.util.UUID

object ICalGenerator {

    /**
     * Builds a Calendar object programmatically using ICal4j 4.x syntax.
     */
    fun buildAcademicCalendar(): Calendar {
        val calendar = Calendar()
        
        // Calendar Level Properties (Commented out temporarily to unblock build)
        // calendar.add<PropertyContainer>(ProdId("-//BorinquenTerrier//CEF//EN"))
        // calendar.add<PropertyContainer>(Version.V2_0)
        // calendar.add<PropertyContainer>(Method.PUBLISH)

        // 1. Create a VEvent (e.g., a Class Lecture)
        val startDateTime = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val endDateTime = startDateTime.plusHours(1)
        
        val event = VEvent(startDateTime, endDateTime, "CS 101 Lecture")
        event.add<PropertyContainer>(Uid(UUID.randomUUID().toString()))
        event.add<PropertyContainer>(Description("Introduction to Computer Science"))
        
        // Recurrence: Weekly on Mondays for 5 weeks
        val recur = net.fortuna.ical4j.model.Recur<Temporal>("FREQ=WEEKLY;BYDAY=MO;COUNT=5")
        event.add<PropertyContainer>(RRule<Temporal>(recur))

        // Attendee with CN and Role
        val attendee = Attendee("mailto:professor@university.edu")
        attendee.add<Property>(Cn("Dr. Smith"))
        attendee.add<Property>(Role.CHAIR)
        event.add<PropertyContainer>(attendee)

        calendar.add<ComponentContainer<CalendarComponent>>(event)

        // 2. Create a VToDo (e.g., an Assignment)
        val todo = VToDo(startDateTime, "Finish Lab 1")
        todo.add<PropertyContainer>(Uid(UUID.randomUUID().toString()))
        todo.add<PropertyContainer>(Description("Complete the first lab assignment on recursion"))
        
        calendar.add<ComponentContainer<CalendarComponent>>(todo)

        // Validate
        calendar.validate()
        
        return calendar
    }

    /**
     * Converts a Calendar object to its String representation.
     */
    fun calendarToString(calendar: Calendar): String {
        val writer = StringWriter()
        val outputter = CalendarOutputter()
        outputter.output(calendar, writer)
        return writer.toString()
    }
}
