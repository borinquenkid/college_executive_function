package com.borinquenterrier.cef

data class DecompositionResult(val stepCount: Int, val deliverableCount: Int, val statusMessage: String)

internal class AutoDecomposer(
    private val repository: CalendarAgent,
    private val decompositionService: TaskDecompositionService
) : AgentAction<Unit, DecompositionResult> {

    override suspend fun run(input: Unit, calendarId: String): DecompositionResult {
        val unplanned = repository.getEvents(calendarId).filter { event ->
            (event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS) &&
                event.studyPlanStart == null
        }
        if (unplanned.isEmpty()) {
            return DecompositionResult(0, 0, "All deliverables already have study plans.")
        }
        var totalSteps = 0
        for (event in unplanned) {
            val tasks = decompositionService.decompose(event)
            if (tasks.isNotEmpty()) {
                totalSteps += decompositionService.applyDecomposition(event, tasks, calendarId)
            }
        }
        val message = if (totalSteps > 0)
            "$totalSteps study steps added for ${unplanned.size} deliverable(s)."
        else
            "Deliverables already have study plans or no steps could be scheduled."
        return DecompositionResult(totalSteps, unplanned.size, message)
    }
}
