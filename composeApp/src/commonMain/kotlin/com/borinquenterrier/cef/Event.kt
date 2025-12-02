package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

enum class EventSource {
    ROUTINE, AI_GENERATED, MANUAL
}

@Serializable
data class Recurrence(
    val daysOfWeek: List<DayOfWeek>,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate
)

sealed interface Event {
    val title: String
    val source: EventSource
}

@Serializable
data class TimeEvent(
    override val title: String,
    override val source: EventSource,
    @Serializable(with = LocalTimeSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class)
    val endTime: LocalTime,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val recurrence: Recurrence? = null
) : Event

@Serializable
data class DayEvent(
    override val title: String,
    override val source: EventSource,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val recurrence: Recurrence? = null
) : Event
