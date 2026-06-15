package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * Asks the AI to break a calendar [Event] down into sub-steps, and applies an accepted
 * decomposition back onto the calendar as STUDY_BLOCK events scheduled before the
 * target's due date.
 */
class TaskDecompositionService(
    private val aiService: AIService,
    private val repository: CalendarAgent,
    private val logger: Logger? = null
) {
    private val tag = "TaskDecompositionService"

    suspend fun decompose(event: Event): List<DecomposedTask> =
        aiService.decomposeTask(event.title, event.date.toString())

    /**
     * Records [target]'s study-plan start date (the earliest step's date) and saves
     * each [tasks] step as a STUDY_BLOCK event, skipping any that collide. Returns the
     * number of steps successfully added.
     */
    suspend fun applyDecomposition(
        target: Event,
        tasks: List<DecomposedTask>,
        calendarId: String
    ): Int {
        val earliestTaskDate = tasks.minOfOrNull {
            target.date.minus(it.daysBeforeDue, DateTimeUnit.DAY)
        }
        val updatedTarget = when (target) {
            is TimeEvent -> target.copy(studyPlanStart = earliestTaskDate?.toString())
            is DayEvent -> target.copy(studyPlanStart = earliestTaskDate?.toString())
        }
        repository.updateEvent(updatedTarget, calendarId)

        var count = 0
        for (task in tasks) {
            val taskDate = target.date.minus(task.daysBeforeDue, DateTimeUnit.DAY)
            val event = DayEvent(
                title = task.title,
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                date = taskDate
            )
            try {
                repository.saveEvent(event, calendarId)
                count++
            } catch (e: OverlapException) {
                logger?.e(tag, "Conflict detected while applying decomposition for task: ${task.title}", e)
                // Skip conflicting steps and continue
            }
        }
        return count
    }
}
