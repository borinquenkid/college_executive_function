package com.borinquenterrier.cef

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentContainer
import net.fortuna.ical4j.model.PropertyContainer
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Method
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Version
import java.io.StringWriter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.UUID

object ICalGenerator {

    /**
     * Builds a Calendar object programmatically using ICal4j 4.x syntax.
     */
    fun buildAcademicCalendar(events: List<Event>): Calendar {
        val calendar = Calendar()

        // Calendar Level Properties
        calendar.add<PropertyContainer>(ProdId("-//BorinquenTerrier//CEF//EN"))
        calendar.add<PropertyContainer>(Version(net.fortuna.ical4j.model.ParameterList(), "2.0"))
        calendar.add<PropertyContainer>(Method("PUBLISH"))

        for (event in events) {
            when (event) {
                is TimeEvent -> {
                    // Start and end date times in system default zone
                    val date = event.date
                    val startTime = event.startTime
                    val endTime = event.endTime

                    val startDateTime = ZonedDateTime.of(
                        date.year, date.monthNumber, date.dayOfMonth,
                        startTime.hour, startTime.minute, startTime.second, 0,
                        ZoneId.systemDefault()
                    )
                    val endDateTime = ZonedDateTime.of(
                        date.year, date.monthNumber, date.dayOfMonth,
                        endTime.hour, endTime.minute, endTime.second, 0,
                        ZoneId.systemDefault()
                    )

                    val vEvent = VEvent(startDateTime, endDateTime, event.title)
                    val uid = event.id ?: UUID.randomUUID().toString()
                    vEvent.add<PropertyContainer>(Uid(uid))

                    val descriptionParts = mutableListOf<String>()
                    descriptionParts.add("Source: ${event.source}")
                    descriptionParts.add("Category: ${event.category}")
                    if (event.warning != null) {
                        descriptionParts.add("Warning: ${event.warning}")
                    }
                    vEvent.add<PropertyContainer>(Description(descriptionParts.joinToString("\n")))

                    if (event.recurrence != null) {
                        val daysStr = event.recurrence.daysOfWeek.mapNotNull {
                            when (it) {
                                kotlinx.datetime.DayOfWeek.MONDAY -> "MO"
                                kotlinx.datetime.DayOfWeek.TUESDAY -> "TU"
                                kotlinx.datetime.DayOfWeek.WEDNESDAY -> "WE"
                                kotlinx.datetime.DayOfWeek.THURSDAY -> "TH"
                                kotlinx.datetime.DayOfWeek.FRIDAY -> "FR"
                                kotlinx.datetime.DayOfWeek.SATURDAY -> "SA"
                                kotlinx.datetime.DayOfWeek.SUNDAY -> "SU"
                                else -> null
                            }
                        }.joinToString(",")

                        val untilStr = "${
                            event.recurrence.endDate.year.toString().padStart(4, '0')
                        }${
                            event.recurrence.endDate.monthNumber.toString().padStart(2, '0')
                        }${event.recurrence.endDate.dayOfMonth.toString().padStart(2, '0')}"
                        val ruleBuilder = StringBuilder("FREQ=WEEKLY")
                        if (daysStr.isNotEmpty()) {
                            ruleBuilder.append(";BYDAY=").append(daysStr)
                        }
                        ruleBuilder.append(";UNTIL=").append(untilStr)

                        val recur = net.fortuna.ical4j.model.Recur<Temporal>(ruleBuilder.toString())
                        vEvent.add<PropertyContainer>(RRule<Temporal>(recur))
                    }

                    calendar.add<ComponentContainer<CalendarComponent>>(vEvent)
                }

                is DayEvent -> {
                    val date = event.date
                    val javaDate =
                        java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
                    val javaNextDate = javaDate.plusDays(1)

                    val vEvent = VEvent(javaDate, javaNextDate, event.title)
                    val uid = event.id ?: UUID.randomUUID().toString()
                    vEvent.add<PropertyContainer>(Uid(uid))

                    val descriptionParts = mutableListOf<String>()
                    descriptionParts.add("Source: ${event.source}")
                    descriptionParts.add("Category: ${event.category}")
                    if (event.warning != null) {
                        descriptionParts.add("Warning: ${event.warning}")
                    }
                    vEvent.add<PropertyContainer>(Description(descriptionParts.joinToString("\n")))

                    if (event.recurrence != null) {
                        val daysStr = event.recurrence.daysOfWeek.mapNotNull {
                            when (it) {
                                kotlinx.datetime.DayOfWeek.MONDAY -> "MO"
                                kotlinx.datetime.DayOfWeek.TUESDAY -> "TU"
                                kotlinx.datetime.DayOfWeek.WEDNESDAY -> "WE"
                                kotlinx.datetime.DayOfWeek.THURSDAY -> "TH"
                                kotlinx.datetime.DayOfWeek.FRIDAY -> "FR"
                                kotlinx.datetime.DayOfWeek.SATURDAY -> "SA"
                                kotlinx.datetime.DayOfWeek.SUNDAY -> "SU"
                                else -> null
                            }
                        }.joinToString(",")

                        val untilStr = "${
                            event.recurrence.endDate.year.toString().padStart(4, '0')
                        }${
                            event.recurrence.endDate.monthNumber.toString().padStart(2, '0')
                        }${event.recurrence.endDate.dayOfMonth.toString().padStart(2, '0')}"
                        val ruleBuilder = StringBuilder("FREQ=WEEKLY")
                        if (daysStr.isNotEmpty()) {
                            ruleBuilder.append(";BYDAY=").append(daysStr)
                        }
                        ruleBuilder.append(";UNTIL=").append(untilStr)

                        val recur = net.fortuna.ical4j.model.Recur<Temporal>(ruleBuilder.toString())
                        vEvent.add<PropertyContainer>(RRule<Temporal>(recur))
                    }

                    calendar.add<ComponentContainer<CalendarComponent>>(vEvent)
                }
            }
        }

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
