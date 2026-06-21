package com.borinquenterrier.cef

sealed interface ResolutionResult {
    data class Success(val resolvedEvents: List<Event>) : ResolutionResult
    data class Conflict(val event: Event) : ResolutionResult
}

class SchedulingAlgorithm(
    private val maxDepth: Int = 3,
    private val validator: ConstraintValidator,
    private val detector: CollisionDetector
) {
    fun resolve(
        event: Event,
        existingEvents: List<Event>,
        depth: Int = 0
    ): ResolutionResult {
        if (depth > maxDepth) {
            return ResolutionResult.Conflict(event)
        }

        // Only STUDY_BLOCK events are subject to scheduling constraints (working hours,
        // meal breaks, collision avoidance). Every other event — deadlines, class sessions,
        // holidays, etc. — has a fixed time set by the institution and is pushed as-is.
        // Two deadlines on the same day or at the same time is valid and common.
        val isSchedulable = event is TimeEvent && event.category == AcademicCategory.STUDY_BLOCK
        if (!isSchedulable) {
            return ResolutionResult.Success(listOf(event))
        }

        val colliding = existingEvents.filter { it.overlaps(event) }
        val isValid = validator.isValidTimeSlot(
            event.date,
            (event as TimeEvent).startTime,
            event.endTime,
            event.priority,
            existingEvents
        )

        if (colliding.isEmpty() && isValid) {
            return ResolutionResult.Success(listOf(event))
        }

        return shiftEvent(event, existingEvents, depth)
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

    private fun findNextAvailableSlot(
        event: Event,
        existingEvents: List<Event>,
        skipCurrent: Boolean = false
    ): Event? {
        return when (event) {
            is DayEvent -> detector.findNextDaySlot(event, existingEvents, validator, skipCurrent)
            is TimeEvent -> detector.findNextTimeSlot(event, existingEvents, validator, skipCurrent)
        }
    }
}
