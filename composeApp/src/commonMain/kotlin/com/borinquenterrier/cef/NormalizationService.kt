package com.borinquenterrier.cef

import kotlinx.datetime.LocalTime


/**
 * A programmatic implementation of [EventExtractor] that uses keyword matching
 * to categorize raw events into academic milestones.
 */
class NormalizationService : EventExtractor {

    override fun extract(events: List<Event>): List<Event> {
        return events.map { event ->
            // Do not override categories that AI has explicitly tagged as STUDY_BLOCK or CLASS
            if (event.category == AcademicCategory.STUDY_BLOCK || event.category == AcademicCategory.CLASS) {
                return@map sanitizeTimes(event)
            }

            val title = event.title.lowercase()
            // "Final Grades Due/Close" mention a grade but aren't a graded submission or exam —
            // don't let the bare "final"/"due" keywords below claim them.
            val mentionsGrades = title.contains("grade")
            // "Last Day of [Summer] Term/Semester/Session" is a registrar term boundary, not a
            // deadline — distinguish it from a bare "last day" deadline phrase (e.g. "last day to
            // submit...") by requiring a term-length word alongside it.
            val isTermBoundaryPhrase = title.contains("last day") &&
                (title.contains("term") || title.contains("semester") || title.contains("session"))

            val newCategory = when {
                title.contains("holiday") || title.contains("break") || title.contains("no class") ->
                    AcademicCategory.HOLIDAY

                isTermBoundaryPhrase || title.contains("semester start") || title.contains("semester end") ||
                    title.contains("add/drop") || title.contains("withdrawal") ->
                    AcademicCategory.SEMESTER_BOUND

                // Force this even when the AI itself mistags grade-posting housekeeping as
                // FINALS/DEADLINE — the "mentionsGrades" guards below only stop OUR keyword rules
                // from doing that, they don't undo a wrong category the AI already assigned.
                mentionsGrades ->
                    AcademicCategory.REGULAR

                title.contains("final") || title.contains("exam") ->
                    AcademicCategory.FINALS

                title.contains("deadline") || title.contains("last day") || title.contains("due") ->
                    AcademicCategory.DEADLINE

                else -> event.category
            }

            // Create a copy with the new category
            val updated = when (event) {
                is TimeEvent -> event.copy(category = newCategory)
                is DayEvent -> event.copy(category = newCategory)
            }
            sanitizeTimes(updated)
        }
    }

    private fun sanitizeTimes(event: Event): Event {
        if (event !is TimeEvent) return event
        if (event.startTime < event.endTime) return event

        val newEnd = if (event.startTime == LocalTime(23, 59)) {
            return event.copy(
                startTime = LocalTime(23, 58),
                endTime = LocalTime(23, 59)
            )
        } else {
            val nextHour = (event.startTime.hour + 1).coerceAtMost(23)
            LocalTime(nextHour, event.startTime.minute)
        }
        return event.copy(endTime = newEnd)
    }
}

