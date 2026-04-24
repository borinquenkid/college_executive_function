package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

import com.russhwolf.settings.Settings

expect class AIService(settings: Settings) {
    suspend fun generateChatResponse(prompt: String): String
    suspend fun generateCalendarEvents(prompt: String): List<Event>
}

@Composable
expect fun rememberAIService(): AIService
