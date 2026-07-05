package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Guards against saving a recurrence window that can never fire — e.g. a stale
 * hardcoded default that nobody updated before hitting Save.
 */
object RoutineWindowValidator {
    fun isFullyInPast(endDate: LocalDate, today: LocalDate): Boolean = endDate < today
}
