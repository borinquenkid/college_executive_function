package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase

/**
 * JVM Implementation of AIService using Gemini.
 */
actual class AIService actual constructor(
    private val settings: Settings,
    private val logger: Logger,
    private val database: AppDatabase?,
    private val modelPath: String?
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
        return "Chat not yet implemented with Gemini."
    }

    actual suspend fun generateCalendarEvents(parts: List<SourcePart>): List<Event> {
        return getGeminiService().generateCalendarEvents(parts)
    }

    actual suspend fun generateStudyPlan(syllabusText: String): List<Event> {
        return getGeminiService().generateCalendarEventsFromPrompt(
            AiPrompts.getSyllabusStudyPlanPrompt(syllabusText)
        )
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    val logger = rememberLogger()
    val driverFactory = rememberDriverFactory()
    val database = remember(driverFactory) { AppDatabase(driverFactory.createDriver()) }
    val modelDir = rememberModelDirectoryPath()
    val modelPath = remember(modelDir) { "$modelDir/Qwen3.5-9B-Q4_K_M.gguf" }
    
    return remember(settings, logger, database, modelPath) { 
        AIService(settings, logger, database, modelPath) 
    }
}
