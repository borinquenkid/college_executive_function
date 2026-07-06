package com.borinquenterrier.cef

import kotlin.time.Clock
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
) : AgentAction<Int, DecompositionResult> {

    /**
     * @param input maximum number of deliverables to decompose this run — the nearest-due ones
     * are picked first. Callers that want everything decomposed in one pass (e.g. a manual,
     * user-initiated action) pass [Int.MAX_VALUE]. Background/poll-driven callers should pass a
     * small cap so a large backlog of unplanned deliverables doesn't burn through a big chunk of
     * the day's AI quota in one run — see AgentHarness.
     */
    override suspend fun run(input: Int, calendarId: String): DecompositionResult {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val unplanned = repository.getEvents(calendarId).filter { event ->
            (event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS) &&
                event.studyPlanStart == null
        }
        if (unplanned.isEmpty()) {
            return DecompositionResult(0, 0, 0, "All deliverables already have study plans.")
        }
        val (allFuture, pastDue) = unplanned.partition { it.date >= today }
        val future = allFuture.sortedBy { it.date }.take(input)
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
