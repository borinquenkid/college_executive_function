package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.borinquenterrier.cef.db.AppDatabase

/**
 * The ContextAgent is responsible for "Document Intelligence".
 * It extracts semantic rules (grading, policies) and manages document Q&A.
 */
class ContextAgent(
    private val aiService: AIService,
    private val database: AppDatabase,
    private val logger: Logger? = null
) {
    private val tag = "ContextAgent"

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    /**
     * Performs a deep analysis of a source to extract grading scales, policies, and rules.
     * Persists the results in the SourceEntity metadata.
     */
    suspend fun analyzeSource(source: SourceItem) {
        _isAnalyzing.value = true
        try {
            val fullText = source.fragments.joinToString("\n\n") { it.text }
            val metadataJson = aiService.analyzeDocument(fullText)
            
            if (metadataJson != null) {
                database.appDatabaseQueries.insertSource(
                    id = source.title,
                    title = source.title,
                    originUri = null, // Should be passed in or looked up
                    type = if (source.fragments.any { it.type == SourceType.CALENDAR }) "CALENDAR" else "TEXT",
                    metadata = metadataJson,
                    updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                )
                logger?.d(tag, "Successfully analyzed and persisted metadata for ${source.title}")
            }
        } catch (e: Exception) {
            logger?.e(tag, "Failed to analyze source ${source.title}", e)
        } finally {
            _isAnalyzing.value = false
        }
    }

    /**
     * Retrieves the semantic metadata for a specific source.
     */
    fun getSourceMetadata(sourceId: String): String? {
        return database.appDatabaseQueries.selectSourceById(sourceId).executeAsOneOrNull()?.metadata
    }

    /**
     * Answers a question based on the specific source and its metadata.
     */
    suspend fun querySource(source: SourceItem, question: String): String {
        val metadata = getSourceMetadata(source.title) ?: ""
        val fragments = source.fragments.joinToString("\n\n") { "Page ${it.pageNumber ?: ""}: ${it.text}" }
        
        val prompt = """
            Context: This is information from the document "${source.title}".
            
            Rules of the Game (Metadata):
            $metadata
            
            Full Content:
            $fragments
            
            Question:
            $question
            
            Instruction: Provide a concise, helpful answer based ONLY on the provided context. 
            If the answer is not in the text, say you don't know.
        """.trimIndent()

        return aiService.generateChatResponse(prompt)
    }
}
