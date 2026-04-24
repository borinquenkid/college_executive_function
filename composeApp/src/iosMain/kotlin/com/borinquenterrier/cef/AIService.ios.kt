package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings

/**
 * iOS Implementation of AIService using Gemini.
 */
actual class AIService actual constructor(private val settings: Settings) {
    
    actual fun isConfigured(): Boolean {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        val accessToken = settings.getString("GOOGLE_ACCESS_TOKEN", "")
        return apiKey.isNotBlank() || accessToken.isNotBlank()
    }

    private fun getGeminiService(): GeminiAIService {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        val accessToken = settings.getString("GOOGLE_ACCESS_TOKEN", "")
        return GeminiAIService(
            apiKey = apiKey.takeIf { it.isNotBlank() },
            accessToken = accessToken.takeIf { it.isNotBlank() }
        )
    }

    actual suspend fun generateChatResponse(prompt: String): String {
        return "Chat not yet implemented with real AI."
    }

    actual suspend fun generateCalendarEvents(prompt: String): List<Event> {
        return getGeminiService().generateCalendarEvents(prompt)
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    return remember { AIService(settings) }
}
