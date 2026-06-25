package com.borinquenterrier.cef

import kotlinx.datetime.LocalTime

object EventTimeRepairer {
    fun repair(event: Event): Event {
        if (event !is TimeEvent || event.endTime > event.startTime) return event
        val plusHourMins = event.startTime.hour * 60 + event.startTime.minute + 60
        return if (plusHourMins < 24 * 60)
            event.copy(endTime = LocalTime(plusHourMins / 60, plusHourMins % 60))
        else
            DayEvent(
                title = event.title,
                source = event.source,
                date = event.date,
                category = event.category,
                warning = event.warning,
                gradeWeight = event.gradeWeight
            )
    }
}
