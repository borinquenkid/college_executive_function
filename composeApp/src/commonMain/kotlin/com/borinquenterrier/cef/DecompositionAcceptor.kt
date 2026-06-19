package com.borinquenterrier.cef

data class AcceptInput(val tasks: List<DecomposedTask>, val target: Event?)

internal class DecompositionAcceptor(
    private val decompositionService: TaskDecompositionService
) : AgentAction<AcceptInput, Int> {

    override suspend fun run(input: AcceptInput, calendarId: String): Int {
        val target = input.target ?: return 0
        if (input.tasks.isEmpty()) return 0
        return decompositionService.applyDecomposition(target, input.tasks, calendarId)
    }
}
