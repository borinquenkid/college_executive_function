package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings

// A dummy implementation for now. We'll replace this with a real AI service later.
actual class AIService(private val settings: Settings) {
    actual suspend fun generateChatResponse(prompt: String): String {
        return "This is a dummy response. The prompt was: $prompt"
    }

    actual suspend fun generateCalendarEvents(prompt: String): List<Event> {
        // Dummy implementation. We'll replace this with a real AI service later.
        return emptyList()
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    return remember { AIService(settings) }
}
