package com.borinquenterrier.cef

class StudyBlockOverrideLogger(
    private val localRepo: StudentCalendarRepository,
    private val prefMemory: UserPreferenceMemoryRepository?
) {
    suspend fun checkMove(event: Event, calendarId: String) {
        val original = localRepo.getAllEvents(calendarId).find { it.id == event.id }
        if (original != null && original.category == AcademicCategory.STUDY_BLOCK) {
            val hasMoved = original.date != event.date ||
                (original is TimeEvent && event is TimeEvent &&
                    (original.startTime != event.startTime || original.endTime != event.endTime))
            if (hasMoved) prefMemory?.logOverride(OverrideAction.MOVE, original)
        }
    }

    suspend fun checkDelete(eventId: String, calendarId: String) {
        val event = localRepo.getAllEvents(calendarId).find { it.id == eventId }
        if (event != null && event.category == AcademicCategory.STUDY_BLOCK) {
            prefMemory?.logOverride(OverrideAction.DELETE, event)
        }
    }
}
