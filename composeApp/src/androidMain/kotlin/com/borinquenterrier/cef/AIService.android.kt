package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase

/**
 * Android Implementation of AIService using Gemini.
 */
actual class RealAIService actual constructor(
    private val settings: Settings,
    private val logger: Logger,
    private val database: AppDatabase?
) : AIService {
    
    actual override fun isConfigured(): Boolean {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        return apiKey.isNotBlank()
    }

    private fun getGeminiService(): GeminiAIService {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        return GeminiAIService(
            apiKey = apiKey,
            logger = logger,
            database = database,
            settings = settings
        )
    }

    actual override suspend fun generateChatResponse(prompt: String): String {
        return getGeminiService().generateChatResponse(prompt)
    }

    actual override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        return getGeminiService().generateCalendarEvents(fragments)
    }

    actual override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        return getGeminiService().generateStudyPlan(syllabusText, existingSchedule, preferences)
    }

    actual override suspend fun analyzeDocument(text: String): String? {
        return getGeminiService().analyzeDocument(text)
    }

    actual override suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> {
        return getGeminiService().decomposeTask(taskTitle, dueDate)
    }

    actual override suspend fun categorizeSource(text: String): SourceCategory {
        return getGeminiService().categorizeSource(text)
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    val logger = rememberLogger()
    val driverFactory = rememberDriverFactory()
    val database = remember(driverFactory) { AppDatabase(driverFactory.createDriver()) }

    return remember(settings, logger, database) { 
        CriticActorAIService(
            RecursiveDecompositionAIService(RealAIService(settings, logger, database)),
            logger
        )
    }
}
