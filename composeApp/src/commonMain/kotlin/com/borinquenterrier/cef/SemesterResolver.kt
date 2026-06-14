package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object SemesterResolver {
    /**
     * Determines the active semester date range based on the current date.
     * Fall semester is defined as Aug 1 - Dec 31.
     * Spring semester is defined as Jan 1 - May 31.
     * Summer/interim defaults to today to today + 30 days.
     */
    fun getSemesterRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val currentYear = today.year
        val isFirstSemester = today.monthNumber in 8..12
        val isSecondSemester = today.monthNumber in 1..5

        return when {
            isFirstSemester -> {
                LocalDate(currentYear, 8, 1) to LocalDate(currentYear, 12, 31)
            }

            isSecondSemester -> {
                LocalDate(currentYear, 1, 1) to LocalDate(currentYear, 5, 31)
            }

            else -> {
                today to today.plus(30, DateTimeUnit.DAY)
            }
        }
    }

    /**
     * Resolves the view range by taking the base semester/interim range and expanding it
     * to include the dates of any provided events.
     */
    fun getExpandedRange(today: LocalDate, events: List<Event>): Pair<LocalDate, LocalDate> {
        val baseRange = getSemesterRange(today)
        if (events.isEmpty()) return baseRange

        val dates = events.map { it.date }
        val minEventDate = dates.minOrNull() ?: baseRange.first
        val maxEventDate = dates.maxOrNull() ?: baseRange.second

        val start = if (minEventDate < baseRange.first) minEventDate else baseRange.first
        val end = if (maxEventDate > baseRange.second) maxEventDate else baseRange.second
        return start to end
    }
}
