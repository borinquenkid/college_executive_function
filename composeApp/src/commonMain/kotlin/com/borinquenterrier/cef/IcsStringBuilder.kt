package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek

object IcsStringBuilder {
    fun buildIcsString(events: List<Event>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//BorinquenTerrier//CEF//EN\r\n")
        sb.append("METHOD:PUBLISH\r\n")

        for (event in events) {
            sb.append("BEGIN:VEVENT\r\n")
            val uid = event.id
                ?: "cef-${event.hashCode()}-${event.date.year}${event.date.monthNumber}${event.date.dayOfMonth}"
            sb.append("UID:").append(uid).append("\r\n")

            // Generate timestamp (DTSTAMP)
            sb.append("DTSTAMP:20260603T000000Z\r\n")

            sb.append("SUMMARY:").append(event.title).append("\r\n")

            // Description
            val descriptionParts = mutableListOf<String>()
            descriptionParts.add("Source: ${event.source}")
            descriptionParts.add("Category: ${event.category}")
            if (event.warning != null) {
                descriptionParts.add("Warning: ${event.warning}")
            }
            sb.append("DESCRIPTION:").append(descriptionParts.joinToString("\\n")).append("\r\n")

            when (event) {
                is TimeEvent -> {
                    val startStr = formatDateTime(event.date, event.startTime)
                    val endStr = formatDateTime(event.date, event.endTime)
                    sb.append("DTSTART:").append(startStr).append("\r\n")
                    sb.append("DTEND:").append(endStr).append("\r\n")

                    if (event.recurrence != null) {
                        val rule = buildRecurrenceRule(event.recurrence)
                        sb.append("RRULE:").append(rule).append("\r\n")
                    }
                }

                is DayEvent -> {
                    val startStr = formatDate(event.date)
                    val endStr = formatDate(plusDays(event.date))
                    sb.append("DTSTART;VALUE=DATE:").append(startStr).append("\r\n")
                    sb.append("DTEND;VALUE=DATE:").append(endStr).append("\r\n")

                    if (event.recurrence != null) {
                        val rule = buildRecurrenceRule(event.recurrence)
                        sb.append("RRULE:").append(rule).append("\r\n")
                    }
                }
            }
            sb.append("END:VEVENT\r\n")
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun formatDateTime(
        date: kotlinx.datetime.LocalDate,
        time: kotlinx.datetime.LocalTime
    ): String {
        val y = date.year.toString().padStart(4, '0')
        val m = date.monthNumber.toString().padStart(2, '0')
        val d = date.dayOfMonth.toString().padStart(2, '0')
        val hr = time.hour.toString().padStart(2, '0')
        val min = time.minute.toString().padStart(2, '0')
        val sec = time.second.toString().padStart(2, '0')
        return "$y$m${d}T$hr$min$sec"
    }

    private fun formatDate(date: kotlinx.datetime.LocalDate): String {
        val y = date.year.toString().padStart(4, '0')
        val m = date.monthNumber.toString().padStart(2, '0')
        val d = date.dayOfMonth.toString().padStart(2, '0')
        return "$y$m$d"
    }

    private fun plusDays(date: kotlinx.datetime.LocalDate): kotlinx.datetime.LocalDate =
        kotlinx.datetime.LocalDate.fromEpochDays(date.toEpochDays() + 1)

    private fun buildRecurrenceRule(recurrence: Recurrence): String {
        val daysStr = recurrence.daysOfWeek.mapNotNull {
            when (it) {
                DayOfWeek.MONDAY -> "MO"
                DayOfWeek.TUESDAY -> "TU"
                DayOfWeek.WEDNESDAY -> "WE"
                DayOfWeek.THURSDAY -> "TH"
                DayOfWeek.FRIDAY -> "FR"
                DayOfWeek.SATURDAY -> "SA"
                DayOfWeek.SUNDAY -> "SU"
                else -> null
            }
        }.joinToString(",")

        val untilStr = formatDate(recurrence.endDate)
        val sb = StringBuilder("FREQ=WEEKLY")
        if (daysStr.isNotEmpty()) {
            sb.append(";BYDAY=").append(daysStr)
        }
        sb.append(";UNTIL=").append(untilStr)
        return sb.toString()
    }
}
