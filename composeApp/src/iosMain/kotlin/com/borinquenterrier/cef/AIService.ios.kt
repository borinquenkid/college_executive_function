package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase

/**
 * iOS Implementation of AIService.
 * Uses Gemini as primary engine and Llamatik as local fallback.
 */
actual class AIService actual constructor(
    private val settings: Settings,
    private val logger: Logger,
    private val database: AppDatabase?,
    private val modelPath: String?
) {
    
    actual fun isConfigured(): Boolean {
        // First check if Gemini is configured via API key
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        if (apiKey.isNotBlank()) return true
        
        // Fallback to checking if a local model is available
        return !modelPath.isNullOrBlank()
    }

    private fun getGeminiService(): GeminiAIService {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        return GeminiAIService(
            apiKey = apiKey,
            logger = logger,
            database = database
        )
    }

    private fun getLlamatikService(): LlamatikAIService {
        val path = modelPath ?: settings.getString("LLAMATIK_MODEL_PATH", "model.gguf")
        return LlamatikAIService(
            modelPath = path,
            logger = logger
        )
    }

    actual suspend fun generateChatResponse(prompt: String): String {
        return "Chat not yet implemented on iOS."
    }

    actual suspend fun generateCalendarEvents(parts: List<SourcePart>): List<Event> {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        return if (apiKey.isNotBlank()) {
            getGeminiService().generateCalendarEvents(parts)
        } else {
            getLlamatikService().generateCalendarEvents(parts)
        }
    }

    actual suspend fun generateStudyPlan(syllabusText: String): List<Event> {
        val apiKey = settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))
        val prompt = AiPrompts.getSyllabusStudyPlanPrompt(syllabusText)
        return if (apiKey.isNotBlank()) {
            getGeminiService().generateCalendarEventsFromPrompt(prompt)
        } else {
            getLlamatikService().generateEventsFromRawPrompt(prompt)
        }
    }
}

@Composable
actual fun rememberAIService(): AIService {
    val settings = rememberSettings()
    val logger = rememberLogger()
    val driverFactory = rememberDriverFactory()
    val database = remember(driverFactory) { AppDatabase(driverFactory.createDriver()) }
    val modelDir = rememberModelDirectoryPath()
    val modelPath = remember(modelDir) { "$modelDir/Qwen3-1.7B-Q4_K_M.gguf" }

    return remember(settings, logger, database, modelPath) { 
        AIService(settings, logger, database, modelPath) 
    }
}
