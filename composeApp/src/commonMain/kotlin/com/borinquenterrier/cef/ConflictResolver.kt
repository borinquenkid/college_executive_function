package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Resolves scheduling conflicts between calendar events and proposed events.
 * Strategy:
 * - MOVABLE events (Study, HW): auto-reschedule to earlier available slot
 * - IMMOVABLE events (Quiz, Test, Exam): flag as unresolved, require professor approval
 */
class ConflictResolver(private val logger: Logger? = null) {
    private val tag = "ConflictResolver"

    data class ConflictResolution(
        val merged: List<Event>,
        val unresolved: List<UnresolvedConflict>
    )

    data class UnresolvedConflict(
        val title: String,
        val date: LocalDate,
        val reason: String,
        val requiresProfessorApproval: Boolean
    )

    fun resolveConflicts(calendar: List<Event>, proposed: List<Event>): ConflictResolution {
        val merged = mutableListOf<Event>()
        val unresolved = mutableListOf<UnresolvedConflict>()

        // Start with calendar events
        merged.addAll(calendar)

        // Process each proposed event
        for (proposed in proposed) {
            val conflict = findConflict(merged, proposed)

            if (conflict == null) {
                // No conflict, add as-is
                merged.add(proposed)
                logger?.d(tag, "No conflict for ${proposed.title}")
            } else {
                when {
                    isMovable(proposed) -> {
                        // Try to reschedule to earlier slot
                        val rescheduled = rescheduleEarlier(proposed, merged)
                        if (rescheduled != null) {
                            merged.add(rescheduled)
                            logger?.d(tag, "Rescheduled ${proposed.title} earlier")
                        } else {
                            unresolved.add(
                                UnresolvedConflict(
                                    title = proposed.title,
                                    date = proposed.date,
                                    reason = "No available slot found to reschedule earlier",
                                    requiresProfessorApproval = false
                                )
                            )
                            logger?.d(tag, "Could not reschedule ${proposed.title}")
                        }
                    }

                    else -> {
                        // Immovable event (quiz, test, exam)
                        unresolved.add(
                            UnresolvedConflict(
                                title = proposed.title,
                                date = proposed.date,
                                reason = "Cannot auto-reschedule ${proposed.category}. Contact professor to reschedule.",
                                requiresProfessorApproval = true
                            )
                        )
                        logger?.d(tag, "Cannot reschedule ${proposed.title} (${proposed.category})")
                    }
                }
            }
        }

        return ConflictResolution(merged = merged, unresolved = unresolved)
    }

    /**
     * Checks if this event conflicts with any event in the list.
     */
    private fun findConflict(events: List<Event>, target: Event): Event? {
        return events.find { target.overlaps(it) }
    }

    /**
     * Checks if an event can be rescheduled (study, hw, etc.)
     */
    private fun isMovable(event: Event): Boolean {
        return event.category in listOf(AcademicCategory.STUDY_BLOCK, AcademicCategory.REGULAR) ||
                (event is TimeEvent && event.title.contains("HW", ignoreCase = true)) ||
                (event is TimeEvent && event.title.contains("Homework", ignoreCase = true)) ||
                (event is TimeEvent && event.title.contains("Assignment", ignoreCase = true))
    }

    /**
     * Finds an earlier time slot for the event and returns a copy with new time.
     * Tries slots in 1-hour increments going earlier.
     */
    private fun rescheduleEarlier(event: Event, occupiedEvents: List<Event>): Event? {
        if (event !is TimeEvent) return null

        val duration = event.endTime.hour - event.startTime.hour
        var tryTime = LocalTime(event.startTime.hour - 1, event.startTime.minute)

        // Try slots going backwards from current start time
        while (tryTime.hour >= 8) { // Don't schedule before 8 AM
            val rescheduled = event.copy(
                startTime = tryTime,
                endTime = LocalTime(tryTime.hour + duration, tryTime.minute)
            )

            // Check if this slot conflicts with any occupied events
            val hasConflict = occupiedEvents.any { rescheduled.overlaps(it) }
            if (!hasConflict) {
                logger?.d(tag, "Found slot for ${event.title} at ${tryTime}")
                return rescheduled
            }

            tryTime = LocalTime(tryTime.hour - 1, tryTime.minute)
        }

        return null // No available slot found
    }
}
