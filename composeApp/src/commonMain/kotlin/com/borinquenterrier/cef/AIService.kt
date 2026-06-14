package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.Settings

interface AIService {
    fun isConfigured(): Boolean
    suspend fun generateChatResponse(prompt: String): String
    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event>
    suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String = "",
        preferences: StudyPreferences = StudyPreferences(),
    ): List<Event>

    suspend fun analyzeDocument(text: String): String?
    suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask>
    suspend fun categorizeSource(text: String): SourceCategory
}

expect class RealAIService(
    settings: Settings,
    logger: Logger,
    database: AppDatabase? = null,
) : AIService {
    override fun isConfigured(): Boolean
    override suspend fun generateChatResponse(prompt: String): String
    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event>
    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences,
    ): List<Event>

    override suspend fun analyzeDocument(text: String): String?
    override suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask>
    override suspend fun categorizeSource(text: String): SourceCategory
}

