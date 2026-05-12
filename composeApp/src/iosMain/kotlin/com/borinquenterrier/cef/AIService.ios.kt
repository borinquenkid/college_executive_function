package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase

/**
 * iOS Implementation of AIService.
 * Uses Gemini as the primary AI engine.
 */
actual class AIService actual constructor(
    private val settings: Settings,
    private val logger: Logger,
    private val database: AppDatabase?
) {
    
    actual fun isConfigured(): Boolean {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        return apiKey.isNotBlank()
    }

    private fun getGeminiService(): GeminiAIService {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        return GeminiAIService(
            apiKey = apiKey,
            logger = logger,
            database = database
        )
    }

    actual suspend fun generateChatResponse(prompt: String): String {
        return "Chat not yet implemented on iOS."
    }

    actual suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        return getGeminiService().generateCalendarEvents(fragments)
    }

    actual suspend fun generateStudyPlan(syllabusText: String): List<Event> {
        val prompt = AiPrompts.getSyllabusStudyPlanPrompt(syllabusText)
        return getGeminiService().generateCalendarEventsFromPrompt(prompt)
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    val logger = rememberLogger()
    val driverFactory = rememberDriverFactory()
    val database = remember(driverFactory) { AppDatabase(driverFactory.createDriver()) }

    return remember(settings, logger, database) { 
        AIService(settings, logger, database) 
    }
}
