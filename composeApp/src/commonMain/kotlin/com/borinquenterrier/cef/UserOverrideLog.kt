package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek

enum class OverrideAction {
    DELETE, MOVE
}

data class UserPreferenceConstraint(
    val dayOfWeek: DayOfWeek,
    val startHour: Int,
    val endHour: Int
)
