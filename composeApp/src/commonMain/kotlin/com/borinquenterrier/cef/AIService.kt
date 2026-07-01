package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.Settings

interface AIService {
    fun isConfigured(): Boolean
    suspend fun generateChatResponse(prompt: String): String
    suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event>
    suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String = "",
        preferences: StudyPreferences = StudyPreferences()
    ): List<Event>

    suspend fun analyzeDocument(text: String): String?
    suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask>
    suspend fun categorizeSource(text: String): SourceCategory

    /**
     * Extracts text from a binary document (e.g. an image-only/scanned PDF) using the model's
     * native document vision. Returns null when unsupported or extraction fails, so callers keep
     * whatever text they already had. Default: unsupported.
     */
    suspend fun extractTextFromDocument(bytes: ByteArray, mimeType: String = "application/pdf"): String? = null
}

expect class RealAIService(
    settings: Settings,
    logger: Logger,
    database: AppDatabase? = null
) : AIService {
    override fun isConfigured(): Boolean
    override suspend fun generateChatResponse(prompt: String): String
    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event>
    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event>

    override suspend fun analyzeDocument(text: String): String?
    override suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask>
    override suspend fun categorizeSource(text: String): SourceCategory
}

@Composable
expect fun rememberAIService(): AIService
