package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class CollisionDetector(
    private val preferences: StudyPreferences = StudyPreferences()
) {
    private val workingHourStart: LocalTime = LocalTime(preferences.studyStartHour, 0)
    private val workingHourEnd: LocalTime = LocalTime(preferences.studyEndHour, 0)

    fun findNextDaySlot(
        event: DayEvent,
        existingEvents: List<Event>,
        validator: ConstraintValidator,
        skipCurrent: Boolean
    ): DayEvent? {
        // Search backward up to 7 days
        for (i in 1..7) {
            val candidateDate = event.date.minus(i, DateTimeUnit.DAY)
            if (skipCurrent && candidateDate == event.date) continue
            if (validator.isDayAvailable(candidateDate, event.priority, existingEvents)) {
                return event.copy(date = candidateDate)
            }
        }

        // Search forward up to 3 days (late leeway)
        for (i in 1..3) {
            val candidateDate = event.date.plus(i, DateTimeUnit.DAY)
            if (skipCurrent && candidateDate == event.date) continue
            if (validator.isDayAvailable(candidateDate, event.priority, existingEvents)) {
                return event.copy(
                    date = candidateDate,
                    warning = "Study block scheduled late/after deadline"
                )
            }
        }

        return null
    }

    fun findNextTimeSlot(
        event: TimeEvent,
        existingEvents: List<Event>,
        validator: ConstraintValidator,
        skipCurrent: Boolean
    ): TimeEvent? {
        val durationSeconds = event.endTime.toSecondOfDay() - event.startTime.toSecondOfDay()
        
        // 1. Try different slots on the same day first
        val sameDaySlot = findTimeSlotOnDay(event.date, durationSeconds, event, existingEvents, validator, skipCurrent)
        if (sameDaySlot != null) return sameDaySlot

        // 2. Search backward day-by-day (up to 7 days)
        for (i in 1..7) {
            val candidateDate = event.date.minus(i, DateTimeUnit.DAY)
            val slot = findTimeSlotOnDay(candidateDate, durationSeconds, event, existingEvents, validator, skipCurrent = false)
            if (slot != null) return slot
        }

        // 3. Search forward day-by-day (up to 3 days)
        for (i in 1..3) {
            val candidateDate = event.date.plus(i, DateTimeUnit.DAY)
            val slot = findTimeSlotOnDay(candidateDate, durationSeconds, event, existingEvents, validator, skipCurrent = false)
            if (slot != null) {
                return slot.copy(warning = "Study block scheduled late/after deadline")
            }
        }

        return null
    }

    private fun findTimeSlotOnDay(
        date: LocalDate,
        durationSeconds: Int,
        originalEvent: TimeEvent,
        existingEvents: List<Event>,
        validator: ConstraintValidator,
        skipCurrent: Boolean
    ): TimeEvent? {
        val stepSeconds = 30 * 60 // 30-minute intervals
        val startSec = workingHourStart.toSecondOfDay()
        val endSec = workingHourEnd.toSecondOfDay()

        var currentStartSec = startSec
        while (currentStartSec + durationSeconds <= endSec) {
            val candidateStart = LocalTime.fromSecondOfDay(currentStartSec)
            val candidateEnd = LocalTime.fromSecondOfDay(currentStartSec + durationSeconds)

            if (skipCurrent && date == originalEvent.date && candidateStart == originalEvent.startTime) {
                currentStartSec += stepSeconds
                continue
            }

            if (validator.isValidTimeSlot(date, candidateStart, candidateEnd, originalEvent.priority, existingEvents)) {
                return originalEvent.copy(
                    date = date,
                    startTime = candidateStart,
                    endTime = candidateEnd
                )
            }
            currentStartSec += stepSeconds
        }
        return null
    }
}
