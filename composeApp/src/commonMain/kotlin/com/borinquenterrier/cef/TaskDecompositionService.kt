package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import okio.ByteString.Companion.encodeUtf8

/**
 * Asks the AI to break a calendar [Event] down into sub-steps, and applies an accepted
 * decomposition back onto the calendar as STUDY_BLOCK events scheduled before the
 * target's due date.
 */
class TaskDecompositionService(
    private val aiService: AIService,
    private val repository: CalendarAgent,
    private val sourceRepository: SourceRepository? = null,
    private val logger: Logger? = null
) {
    suspend fun decompose(event: Event): List<DecomposedTask> =
        aiService.decomposeTask(event.title, event.date.toString(), resolveSourceContext(event.sourceId))

    /**
     * Looks up the raw text of the document [sourceId] was extracted from, so decomposition
     * can be checked against the same ground truth as the original event extraction. Falls
     * back to "" (unset link, missing source, or a lookup failure) rather than failing the
     * whole decomposition — grounding is a quality improvement, not a hard dependency.
     */
    private suspend fun resolveSourceContext(sourceId: String?): String {
        if (sourceId == null || sourceRepository == null) return ""
        return try {
            sourceRepository.getFragmentsForSource(sourceId).joinToString("\n\n") { it.text }
        } catch (e: Exception) {
            logger?.e("TaskDecompositionService", "Failed to resolve source context for $sourceId", e)
            ""
        }
    }

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
                id = stepId(target.id, task.title, taskDate),
                title = task.title,
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                date = taskDate
            )
            try {
                repository.saveEvent(event, calendarId)
                count++
            } catch (e: OverlapException) {
                // Skip conflicting steps and continue
                println("[TaskDecompositionService] Skipping conflicting study block step '${task.title}': ${e.message}")
            }
        }
        return count
    }

    private fun stepId(parentId: String?, title: String, date: LocalDate): String =
        "$parentId|$title|$date".encodeUtf8().sha256().hex().take(24)
}
