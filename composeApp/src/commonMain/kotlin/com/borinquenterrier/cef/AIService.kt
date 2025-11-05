package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class AIService {
    suspend fun generateChatResponse(prompt: String): String
    suspend fun generateCalendarEvents(prompt: String): List<CalendarEvent>
}

@Composable
expect fun rememberAIService(): AIService
