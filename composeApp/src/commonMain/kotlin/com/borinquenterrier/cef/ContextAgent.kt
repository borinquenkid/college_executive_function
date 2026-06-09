package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight facade coordinating document intelligence services.
 * Delegates to specialized services for fragment ranking and context building.
 */
class ContextAgent(
    private val aiService: AIService,
    private val sourceRepository: SourceRepository,
    private val fragmentRanker: FragmentRanker,
    private val contextBuilder: SourceContextBuilder,
    private val logger: Logger? = null
) {
    private val tag = "ContextAgent"

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    suspend fun analyzeSource(source: SourceItem) {
        _isAnalyzing.value = true
        try {
            val fullText = source.fragments.joinToString("\n\n") { it.text }
            val metadataJson = aiService.analyzeDocument(fullText)

            if (metadataJson != null) {
                sourceRepository.updateSourceMetadata(source.title, metadataJson)
                logger?.d(tag, "Successfully analyzed and persisted metadata for ${source.title}")
            }
        } catch (e: Exception) {
            logger?.e(tag, "Failed to analyze source ${source.title}", e)
        } finally {
            _isAnalyzing.value = false
        }
    }

    suspend fun getSourceMetadata(sourceId: String): String? {
        return sourceRepository.getSourceMetadata(sourceId)
    }

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

    suspend fun queryAllSources(
        sources: List<SourceItem>,
        conversationHistory: List<ChatMessage>,
        question: String
    ): String {
        if (sources.isEmpty()) {
            return "No sources are loaded yet. Please add a syllabus or document from the Sources panel first."
        }

        val topPairs = fragmentRanker.rankFragments(sources, question, topK = 15)

        val sourceBlocks = contextBuilder.buildContextBlocks(topPairs) { sourceId ->
            getSourceMetadata(sourceId)
        }

        val historyPairs = conversationHistory.map { it.author to it.content }
        val prompt = AiPrompts.getMultiSourceChatPrompt(sourceBlocks, historyPairs, question)

        logger?.d(tag, "queryAllSources: ${sources.size} source(s), ${conversationHistory.size} history turns, using top ${topPairs.size} fragments")
        return aiService.generateChatResponse(prompt)
    }
}
