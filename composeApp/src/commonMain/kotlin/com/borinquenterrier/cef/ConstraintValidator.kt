package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ConstraintValidator(
    private val preferences: StudyPreferences = StudyPreferences(),
    private val userConstraints: List<UserPreferenceConstraint> = emptyList()
) {
    private val workingHourStart: LocalTime = LocalTime(preferences.studyStartHour, 0)
    private val workingHourEnd: LocalTime = LocalTime(preferences.studyEndHour, 0)
    private val lunchStart: LocalTime = LocalTime(preferences.lunchStartHour, 0)
    private val lunchEnd: LocalTime = LocalTime(preferences.lunchEndHour, 0)
    private val dinnerStart: LocalTime = LocalTime(preferences.dinnerStartHour, 0)
    private val dinnerEnd: LocalTime = LocalTime(preferences.dinnerEndHour, 0)

    fun isValidTimeSlot(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        priority: Int,
        existingEvents: List<Event>
    ): Boolean {
        // Must not overlap with user preference constraints
        val day = date.dayOfWeek
        userConstraints.forEach { constraint ->
            if (constraint.dayOfWeek == day) {
                val constraintStart = LocalTime(constraint.startHour, 0)
                val constraintEnd = LocalTime(constraint.endHour, 0)
                if (start < constraintEnd && end > constraintStart) {
                    return false
                }
            }
        }

        // Must be within working hours
        if (start < workingHourStart || end > workingHourEnd) return false

        // Must not overlap with lunch break
        if (start < lunchEnd && end > lunchStart) return false

        // Must not overlap with dinner break
        if (start < dinnerEnd && end > dinnerStart) return false

        // Create a temporary event to check for overlaps
        val tempEvent = TimeEvent(
            title = "Temp",
            source = EventSource.MANUAL,
            date = date,
            startTime = start,
            endTime = end
        )

        // Must not overlap with existing events on that day that have equal or higher priority
        return existingEvents.none { it.overlaps(tempEvent) && it.priority >= priority }
    }

    fun isDayAvailable(date: LocalDate, priority: Int, existingEvents: List<Event>): Boolean {
        // A day is available if no event of equal or higher priority exists on that day
        return existingEvents.none { it.date == date && it.priority >= priority }
    }
}
