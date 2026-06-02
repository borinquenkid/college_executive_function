package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed interface ResolutionResult {
    data class Success(val resolvedEvents: List<Event>) : ResolutionResult
    data class Conflict(val event: Event) : ResolutionResult
}

class CollisionResolver(
    private val maxDepth: Int = 3,
    private val workingHourStart: LocalTime = LocalTime(9, 0),
    private val workingHourEnd: LocalTime = LocalTime(21, 0),
    private val lunchStart: LocalTime = LocalTime(12, 0),
    private val lunchEnd: LocalTime = LocalTime(13, 0),
    private val dinnerStart: LocalTime = LocalTime(17, 0),
    private val dinnerEnd: LocalTime = LocalTime(19, 0)
) {

    /**
     * Resolves collisions for a single event against a list of already scheduled/existing events.
     * Returns ResolutionResult.Success with updated events (if rescheduled/bumped successfully) or ResolutionResult.Conflict.
     */
    fun resolve(
        event: Event,
        existingEvents: List<Event>,
        depth: Int = 0
    ): ResolutionResult {
        if (depth > maxDepth) {
            return ResolutionResult.Conflict(event)
        }

        val colliding = existingEvents.filter { it.overlaps(event) }
        if (colliding.isEmpty()) {
            return ResolutionResult.Success(listOf(event))
        }

        // Determine if we can bump all colliding events
        val canBumpAll = colliding.all { event.priority > it.priority }

        if (canBumpAll) {
            val currentExisting = existingEvents.toMutableList()
            currentExisting.removeAll(colliding)
            // Add the bumping event so its slot is occupied
            currentExisting.add(event)

            val bumpedRescheduled = mutableListOf<Event>()
            for (bumped in colliding) {
                // Try to find a new slot for this bumped event
                val res = findNewSlotAndResolve(bumped, currentExisting, depth + 1)
                if (res is ResolutionResult.Success) {
                    bumpedRescheduled.addAll(res.resolvedEvents)
                    currentExisting.addAll(res.resolvedEvents)
                } else {
                    // Rescheduling a bumped event failed. Fall back to shifting the original event instead.
                    return shiftEvent(event, existingEvents, depth)
                }
            }
            return ResolutionResult.Success(bumpedRescheduled + event)
        } else {
            // Cannot bump colliding events, must shift the new event
            return shiftEvent(event, existingEvents, depth)
        }
    }

    private fun findNewSlotAndResolve(
        event: Event,
        existingEvents: List<Event>,
        depth: Int
    ): ResolutionResult {
        val shifted = findNextAvailableSlot(event, existingEvents, skipCurrent = true)
            ?: return ResolutionResult.Conflict(event)
        
        return resolve(shifted, existingEvents, depth)
    }

    private fun shiftEvent(
        event: Event,
        existingEvents: List<Event>,
        depth: Int
    ): ResolutionResult {
        val shifted = findNextAvailableSlot(event, existingEvents, skipCurrent = true)
            ?: return ResolutionResult.Conflict(event)
        
        return resolve(shifted, existingEvents, depth + 1)
    }

    /**
     * Finds the next available slot for the event, searching backward first, then forward.
     */
    fun findNextAvailableSlot(
        event: Event,
        existingEvents: List<Event>,
        skipCurrent: Boolean = false
    ): Event? {
        return when (event) {
            is DayEvent -> findNextDaySlot(event, existingEvents, skipCurrent)
            is TimeEvent -> findNextTimeSlot(event, existingEvents, skipCurrent)
        }
    }

    private fun findNextDaySlot(
        event: DayEvent,
        existingEvents: List<Event>,
        skipCurrent: Boolean
    ): DayEvent? {
        // Search backward up to 7 days
        for (i in 1..7) {
            val candidateDate = event.date.minus(i, DateTimeUnit.DAY)
            if (skipCurrent && candidateDate == event.date) continue
            if (isDayAvailable(candidateDate, event.priority, existingEvents)) {
                return event.copy(date = candidateDate)
            }
        }

        // Search forward up to 3 days (late leeway)
        for (i in 1..3) {
            val candidateDate = event.date.plus(i, DateTimeUnit.DAY)
            if (skipCurrent && candidateDate == event.date) continue
            if (isDayAvailable(candidateDate, event.priority, existingEvents)) {
                return event.copy(
                    date = candidateDate,
                    warning = "Study block scheduled late/after deadline"
                )
            }
        }

        return null
    }

    private fun isDayAvailable(date: LocalDate, priority: Int, existingEvents: List<Event>): Boolean {
        // A day is available if no event of equal or higher priority exists on that day
        return existingEvents.none { it.date == date && it.priority >= priority }
    }

    private fun findNextTimeSlot(
        event: TimeEvent,
        existingEvents: List<Event>,
        skipCurrent: Boolean
    ): TimeEvent? {
        val durationSeconds = event.endTime.toSecondOfDay() - event.startTime.toSecondOfDay()
        
        // 1. Try different slots on the same day first
        val sameDaySlot = findTimeSlotOnDay(event.date, durationSeconds, event, existingEvents, skipCurrent)
        if (sameDaySlot != null) return sameDaySlot

        // 2. Search backward day-by-day (up to 7 days)
        for (i in 1..7) {
            val candidateDate = event.date.minus(i, DateTimeUnit.DAY)
            val slot = findTimeSlotOnDay(candidateDate, durationSeconds, event, existingEvents, skipCurrent = false)
            if (slot != null) return slot
        }

        // 3. Search forward day-by-day (up to 3 days)
        for (i in 1..3) {
            val candidateDate = event.date.plus(i, DateTimeUnit.DAY)
            val slot = findTimeSlotOnDay(candidateDate, durationSeconds, event, existingEvents, skipCurrent = false)
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

            if (isValidTimeSlot(date, candidateStart, candidateEnd, originalEvent.priority, existingEvents)) {
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

    private fun isValidTimeSlot(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        priority: Int,
        existingEvents: List<Event>
    ): Boolean {
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
}
