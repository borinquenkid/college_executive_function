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
    suspend fun generateStudyPlan(syllabusText: String): List<Event>
}

@Composable
expect fun rememberAIService(): AIService
