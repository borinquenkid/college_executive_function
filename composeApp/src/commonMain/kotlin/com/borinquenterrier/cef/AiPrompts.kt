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
        extras: ChatPromptExtras = ChatPromptExtras()
    ): String = ChatBuilder.getMultiSourceChatPrompt(sourceBlocks, conversationHistory, question, extras)

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

/**
 * Secondary, mostly-optional context for [AiPrompts.getMultiSourceChatPrompt] — grouped out of
 * the main parameter list (Sonar S107) since these all default and are rarely all supplied
 * together, unlike [SourceContextBlock]/conversationHistory/question which every call needs.
 */
data class ChatPromptExtras(
    val warnings: List<String> = emptyList(),
    val summary: String? = null,
    // True when the caller (ContextAgent) already sized conversationHistory to fit the token
    // budget — independent of whether a summary exists yet (a long-but-not-yet-folded
    // conversation is still budget-sized and must NOT be re-truncated to MAX_HISTORY_TURNS).
    // False (the legacy default) applies the naive takeLast cut.
    val historyAlreadyBudgeted: Boolean = false,
    // Cross-term memory (ADR 0004 / ROADMAP Phase 13, XM-4): a small, fixed-size distilled
    // summary across a student's prior completed terms, or null below the min-2-terms floor.
    val studentProfile: String? = null,
    // Deadline-safety channel (tasks/plan.md T4): a compact digest of the student's own
    // calendar events from EventsDigestBuilder. Date answers must come from here, not from
    // lexically-retrieved document prose — see the precedence guardrail in getMultiSourceChatPrompt.
    val eventsDigest: String? = null
)
