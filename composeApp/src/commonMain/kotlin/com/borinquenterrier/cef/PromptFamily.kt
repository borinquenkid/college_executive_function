package com.borinquenterrier.cef

/**
 * The four prompt families in the app, mirroring the four [AiPrompts] builder objects.
 *
 * Each family runs through its **own** [GeminiRequestQueue] so a slowdown or timeout storm
 * in one family (e.g. a 42-batch syllabus extraction) cannot starve the others (e.g. chat or
 * categorization). This is the deliberate replacement for the single global queue, which
 * coupled every prompt to the slowest one.
 */
enum class PromptFamily(val queueName: String) {
    /** [EventBuilder]: source event extraction + event critique. */
    EVENT_EXTRACTION("event"),

    /** [StudyPlanBuilder]: syllabus study plan, task decomposition + decomposition critique. */
    STUDY_PLAN("study_plan"),

    /** [CategorizationBuilder]: document intelligence, source categorization, syllabus audit. */
    CATEGORIZATION("categorization"),

    /** [ChatBuilder]: multi-source chat + chat critique. */
    CHAT("chat");
}
