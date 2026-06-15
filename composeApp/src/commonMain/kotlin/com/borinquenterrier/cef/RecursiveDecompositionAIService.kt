package com.borinquenterrier.cef

class RecursiveDecompositionAIService(
    private val delegate: AIService,
    private val logger: Logger? = null,
    private val maxDepth: Int = 3
) : AIService by delegate {

    override suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> {
        val orchestrator = DecompositionOrchestrator(delegate, logger, maxDepth)
        return orchestrator.decompose(taskTitle, dueDate)
    }
}
