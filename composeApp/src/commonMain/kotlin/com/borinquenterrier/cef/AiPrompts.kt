package com.borinquenterrier.cef

/**
 * Lightweight facade for centrally managed prompts.
 * Delegates to 4 specialized builder objects for maintainability.
 */
object AiPrompts {

    fun getSourceEventExtractionPrompt(fragmentJson: String): String =
        EventBuilder.getSourceEventExtractionPrompt(fragmentJson)

    fun getSyllabusStudyPlanPrompt(
        syllabusText: String,
        existingSchedule: String = "",
        preferences: StudyPreferences = StudyPreferences()
    ): String =
        StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule, preferences)

    fun getTaskDecompositionPrompt(
        taskTitle: String,
        dueDate: String,
        context: String = ""
    ): String =
        StudyPlanBuilder.getTaskDecompositionPrompt(taskTitle, dueDate, context)

    fun getDocumentIntelligencePrompt(text: String): String =
        CategorizationBuilder.getDocumentIntelligencePrompt(text)

    fun getPdfVisionExtractionPrompt(): String =
        CategorizationBuilder.getPdfVisionExtractionPrompt()

    fun getSourceCategorizationPrompt(text: String): String =
        CategorizationBuilder.getSourceCategorizationPrompt(text)

    fun getMultiSourceChatPrompt(
        sourceBlocks: List<SourceContextBlock>,
        conversationHistory: List<Pair<String, String>>,
        question: String,
        warnings: List<String> = emptyList(),
        summary: String? = null,
        historyAlreadyBudgeted: Boolean = false,
        studentProfile: String? = null,
        eventsDigest: String? = null
    ): String = ChatBuilder.getMultiSourceChatPrompt(
        sourceBlocks, conversationHistory, question, warnings, summary, historyAlreadyBudgeted, studentProfile,
        eventsDigest
    )

    fun getConversationSummaryPrompt(
        existingSummary: String?,
        turnsToSummarize: List<Pair<String, String>>
    ): String = ChatBuilder.getConversationSummaryPrompt(existingSummary, turnsToSummarize)

    fun getEventCritiquePrompt(sourceText: String, eventsJson: String): String =
        EventBuilder.getEventCritiquePrompt(sourceText, eventsJson)

    fun getChatCritiquePrompt(originalPrompt: String, response: String): String =
        ChatBuilder.getChatCritiquePrompt(originalPrompt, response)

    fun getDecompositionCritiquePrompt(
        taskTitle: String,
        dueDate: String,
        tasksJson: String,
        sourceContext: String = ""
    ): String =
        StudyPlanBuilder.getDecompositionCritiquePrompt(taskTitle, dueDate, tasksJson, sourceContext)

    fun getSyllabusAuditPrompt(syllabusText: String): String =
        CategorizationBuilder.getSyllabusAuditPrompt(syllabusText)
}

/**
 * Holds the distilled context for one source, used by [AiPrompts.getMultiSourceChatPrompt].
 */
data class SourceContextBlock(
    val title: String,
    val category: String,
    val metadata: String?,
    val fragmentText: String
)
