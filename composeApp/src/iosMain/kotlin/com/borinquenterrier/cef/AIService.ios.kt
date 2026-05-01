package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase

/**
 * iOS Implementation of AIService using Llamatik.
 */
actual class AIService actual constructor(
    private val settings: Settings,
    private val logger: Logger,
    private val database: AppDatabase?,
    private val modelPath: String?
) {
    
    actual fun isConfigured(): Boolean {
        return !modelPath.isNullOrBlank()
    }

    private fun getLlamatikService(): LlamatikAIService {
        val path = modelPath ?: settings.getString("LLAMATIK_MODEL_PATH", "model.gguf")
        return LlamatikAIService(
            modelPath = path,
            logger = logger
        )
    }

    actual suspend fun generateChatResponse(prompt: String): String {
        return "Chat not yet implemented with Llamatik."
    }

    actual suspend fun generateCalendarEvents(prompt: String): List<Event> {
        return getLlamatikService().generateCalendarEvents(prompt)
    }

    actual suspend fun generateStudyPlan(syllabusText: String): List<Event> {
        val prompt = AiPrompts.getSyllabusStudyPlanPrompt(syllabusText)
        return getLlamatikService().generateCalendarEvents(prompt)
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
