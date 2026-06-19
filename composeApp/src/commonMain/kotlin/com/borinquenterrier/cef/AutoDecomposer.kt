package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class DecompositionResult(
    val stepCount: Int,
    val deliverableCount: Int,
    val lateCount: Int,
    val statusMessage: String
)

internal class AutoDecomposer(
    private val repository: CalendarAgent,
    private val decompositionService: TaskDecompositionService,
    private val clock: Clock = Clock.System
) : AgentAction<Unit, DecompositionResult> {

    override suspend fun run(input: Unit, calendarId: String): DecompositionResult {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val unplanned = repository.getEvents(calendarId).filter { event ->
            (event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS) &&
                event.studyPlanStart == null
        }
        if (unplanned.isEmpty()) {
            return DecompositionResult(0, 0, 0, "All deliverables already have study plans.")
        }
        val (future, pastDue) = unplanned.partition { it.date >= today }
        var totalSteps = 0
        for (event in future) {
            val tasks = decompositionService.decompose(event)
            if (tasks.isNotEmpty()) {
                totalSteps += decompositionService.applyDecomposition(event, tasks, calendarId)
            }
        }
        val latePart = if (pastDue.isNotEmpty())
            " ${pastDue.size} deadline(s) past due — check with your professor."
        else ""
        val message = if (totalSteps > 0)
            "$totalSteps study steps added for ${future.size} deliverable(s).$latePart"
        else if (future.isEmpty())
            "No upcoming deliverables to plan.$latePart"
        else
            "Deliverables already have study plans or no steps could be scheduled.$latePart"
        return DecompositionResult(totalSteps, future.size, pastDue.size, message)
    }
}
