package com.borinquenterrier.cef

/**
 * A programmatic implementation of [EventExtractor] that uses keyword matching
 * to categorize raw events into academic milestones.
 */
class KeywordEventExtractor : EventExtractor {

    override fun extract(events: List<Event>): List<Event> {
        return events.map { event ->
            val title = event.title.lowercase()
            val newCategory = when {
                title.contains("holiday") || title.contains("break") || title.contains("no class") -> 
                    AcademicCategory.HOLIDAY
                title.contains("final") || title.contains("exam") -> 
                    AcademicCategory.FINALS
                title.contains("deadline") || title.contains("last day") || title.contains("due") -> 
                    AcademicCategory.DEADLINE
                title.contains("semester start") || title.contains("semester end") -> 
                    AcademicCategory.SEMESTER_BOUND
                else -> event.category
            }

            // Create a copy with the new category
            when (event) {
                is TimeEvent -> event.copy(category = newCategory)
                is DayEvent -> event.copy(category = newCategory)
            }
        }
    }
}
