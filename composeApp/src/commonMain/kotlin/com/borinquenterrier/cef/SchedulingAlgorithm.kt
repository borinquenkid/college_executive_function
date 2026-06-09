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

        val colliding = existingEvents.filter { it.overlaps(event) }
        val isValid = when (event) {
            is DayEvent -> true
            is TimeEvent -> validator.isValidTimeSlot(event.date, event.startTime, event.endTime, event.priority, existingEvents)
        }

        if (colliding.isEmpty() && isValid) {
            return ResolutionResult.Success(listOf(event))
        }

        if (!isValid) {
            return shiftEvent(event, existingEvents, depth)
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
