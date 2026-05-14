package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

import com.russhwolf.settings.Settings

import com.borinquenterrier.cef.db.AppDatabase

expect class AIService(
    settings: Settings,
    logger: Logger,
    database: AppDatabase? = null
) {
    fun isConfigured(): Boolean
    suspend fun generateChatResponse(prompt: String): String
    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event>
    suspend fun generateStudyPlan(syllabusText: String, existingSchedule: String = ""): List<Event>
    suspend fun analyzeDocument(text: String): String?
    suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask>
}

@Composable
expect fun rememberAIService(): AIService
