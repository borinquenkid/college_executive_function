package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class RoutineItem(
    val title: String,
    val dayOfWeek: DayOfWeek,
    @Serializable(with = LocalTimeSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class)
    val endTime: LocalTime,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate
)
