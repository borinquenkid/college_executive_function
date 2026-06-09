package com.borinquenterrier.cef

/**
 * Detects overlapping events before saving to prevent scheduling conflicts.
 */
class EventConflictDetector {
    /**
     * Finds any conflicting event in the existing event list.
     * @param newEvent Event to check for conflicts
     * @param existingEvents Current events in the calendar
     * @return The conflicting event if one exists, null otherwise
     */
    fun findConflict(newEvent: Event, existingEvents: List<Event>): Event? {
        return existingEvents.find { existing ->
            existing.id != newEvent.id && existing.overlaps(newEvent)
        }
    }

    /**
     * Validates that adding an event won't cause conflicts.
     * @throws OverlapException if a conflict is found
     */
    fun validateNoConflict(newEvent: Event, existingEvents: List<Event>) {
        val conflict = findConflict(newEvent, existingEvents)
        if (conflict != null) {
            throw OverlapException(existingEvent = conflict, newEvent = newEvent)
        }
    }
}
