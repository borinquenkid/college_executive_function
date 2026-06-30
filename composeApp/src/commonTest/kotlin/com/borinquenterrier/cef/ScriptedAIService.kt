package com.borinquenterrier.cef

/**
 * A scriptable [AIService] standing in for the LLM in pipeline tests. Each generation method is
 * backed by a lambda so a scenario can return confabulated events or throw to simulate an LLM
 * exception. Unused methods return harmless defaults.
 */
class ScriptedAIService(
    private val onStudyPlan: () -> List<Event> = { emptyList() },
    private val onCalendarEvents: () -> List<Event> = { emptyList() },
    private val onChat: (String) -> String = { it },
) : AIService {
    override fun isConfigured(): Boolean = true
    override suspend fun generateChatResponse(prompt: String): String = onChat(prompt)
    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> = onCalendarEvents()
    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> = onStudyPlan()

    override suspend fun analyzeDocument(text: String): String? = null
    override suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> = emptyList()
    override suspend fun categorizeSource(text: String): SourceCategory = SourceCategory.OTHER
}
