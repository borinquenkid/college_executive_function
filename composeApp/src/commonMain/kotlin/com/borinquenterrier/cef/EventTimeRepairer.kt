package com.borinquenterrier.cef

import kotlinx.datetime.LocalTime

object EventTimeRepairer {
    fun repair(event: Event): Event {
        if (event !is TimeEvent || event.endTime > event.startTime) return event
        val plusHourMins = event.startTime.hour * 60 + event.startTime.minute + 60
        val newEnd = if (plusHourMins < 24 * 60)
            LocalTime(plusHourMins / 60, plusHourMins % 60)
        else
            LocalTime(23, 59, 59)
        return event.copy(endTime = newEnd)
    }
}
