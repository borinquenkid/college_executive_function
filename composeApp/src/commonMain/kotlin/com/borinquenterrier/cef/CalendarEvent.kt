package com.borinquenterrier.cef

import kotlinx.datetime.Instant

data class CalendarEvent(
    val title: String,
    val startTime: Instant,
    val endTime: Instant
)
