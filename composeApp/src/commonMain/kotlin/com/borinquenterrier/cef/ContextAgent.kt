package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ContextAgent is responsible for "Document Intelligence".
 * It extracts semantic rules (grading, policies) and manages document Q&A.
 */
class ContextAgent(
    private val aiService: AIService,
    private val sourceRepository: SourceRepository,
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
                sourceRepository.updateSourceMetadata(source.title, metadataJson)
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
    suspend fun getSourceMetadata(sourceId: String): String? {
        return sourceRepository.getSourceMetadata(sourceId)
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

    /**
     * Answers a question by reasoning across ALL loaded sources simultaneously.
     *
     * Sources are sorted by academic relevance before being injected into the context window:
     * SYLLABUS (0) → LECTURE_NOTES (1) → LAB_MANUAL (2) → READING_MATERIAL (3) → OTHER (4).
     * Each source's fragment text is truncated at [AiPrompts.MAX_CHARS_PER_SOURCE] characters
     * to keep the total prompt within model limits.
     *
     * @param sources All available [SourceItem]s, typically from [AppController.sourceItems].
     * @param conversationHistory Prior chat turns for follow-up question coherence.
     * @param question The student's current question.
     */
    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then", "else", "when", 
        "at", "by", "for", "from", "in", "into", "of", "off", "on", "onto", 
        "out", "over", "to", "up", "with", "is", "are", "was", "were", "be", 
        "been", "being", "have", "has", "had", "do", "does", "did", "this", 
        "that", "these", "those", "they", "them", "their", "he", "him", "his", 
        "she", "her", "hers", "it", "its", "you", "your", "yours", "we", "us", "our"
    )

    internal fun rankFragments(
        sources: List<SourceItem>,
        question: String,
        topK: Int = 15
    ): List<Pair<SourceItem, SourceFragment>> {
        val allPairs = sources.flatMap { source ->
            source.fragments.map { fragment -> source to fragment }
        }

        if (allPairs.isEmpty()) {
            return emptyList()
        }

        val queryTerms = question.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()

        if (queryTerms.isEmpty()) {
            return allPairs.take(topK)
        }

        val pairWords = allPairs.map { (_, fragment) ->
            fragment.text.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
        }

        val totalDocuments = allPairs.size.toDouble()
        val dfMap = queryTerms.associateWith { term ->
            val df = pairWords.count { words -> term in words }
            df
        }

        val scoredPairs = allPairs.mapIndexed { index, pair ->
            val words = pairWords[index]
            if (words.isEmpty()) {
                pair to 0.0
            } else {
                var score = 0.0
                val tfMap = words.groupingBy { it }.eachCount()
                for (term in queryTerms) {
                    val tf = tfMap[term] ?: 0
                    if (tf > 0) {
                        val df = dfMap[term] ?: 0
                        val idf = kotlin.math.ln(1.0 + totalDocuments / (1.0 + df))
                        val normalizedTf = tf.toDouble() / words.size.toDouble()
                        score += normalizedTf * idf
                    }
                }
                pair to score
            }
        }

        return scoredPairs
            .sortedWith(compareByDescending<Pair<Pair<SourceItem, SourceFragment>, Double>> { it.second }.thenBy { allPairs.indexOf(it.first) })
            .map { it.first }
            .take(topK)
    }

    /**
     * Answers a question by reasoning across ALL loaded sources simultaneously.
     *
     * Sources are sorted by academic relevance before being injected into the context window:
     * SYLLABUS (0) → LECTURE_NOTES (1) → LAB_MANUAL (2) → READING_MATERIAL (3) → OTHER (4).
     * Relevance ranking (TF-IDF) is applied to select the top-K fragments.
     * Each source's fragment text is truncated at [AiPrompts.MAX_CHARS_PER_SOURCE] characters
     * to keep the total prompt within model limits.
     *
     * @param sources All available [SourceItem]s, typically from [AppController.sourceItems].
     * @param conversationHistory Prior chat turns for follow-up question coherence.
     * @param question The student's current question.
     */
    suspend fun queryAllSources(
        sources: List<SourceItem>,
        conversationHistory: List<ChatMessage>,
        question: String
    ): String {
        if (sources.isEmpty()) {
            return "No sources are loaded yet. Please add a syllabus or document from the Sources panel first."
        }

        val categoryPriority = mapOf(
            SourceCategory.SYLLABUS to 0,
            SourceCategory.LECTURE_NOTES to 1,
            SourceCategory.LAB_MANUAL to 2,
            SourceCategory.READING_MATERIAL to 3,
            SourceCategory.OTHER to 4
        )

        // Rank fragments across all sources (selecting top 15 relevant fragments)
        val topPairs = rankFragments(sources, question, topK = 15)

        // Group selected pairs by their source
        val groupedBySource = topPairs.groupBy { it.first }
        
        // Sort sources that contain relevant fragments based on category priority
        val sortedSourcesWithFragments = groupedBySource.keys.sortedBy { categoryPriority[it.category] ?: 5 }

        val sourceBlocks = sortedSourcesWithFragments.map { source ->
            val metadata = getSourceMetadata(source.title)
            val pairsForSource = groupedBySource[source] ?: emptyList()
            val fragmentText = pairsForSource.joinToString("\n\n") { (_, fragment) ->
                if (fragment.pageNumber != null) "Page ${fragment.pageNumber}: ${fragment.text}"
                else fragment.text
            }
            SourceContextBlock(
                title = source.title,
                category = source.category.name,
                metadata = metadata,
                fragmentText = fragmentText
            )
        }

        val historyPairs = conversationHistory.map { it.author to it.content }
        val prompt = AiPrompts.getMultiSourceChatPrompt(sourceBlocks, historyPairs, question)

        logger?.d(tag, "queryAllSources: ${sources.size} source(s), ${conversationHistory.size} history turns, using top ${topPairs.size} fragments")
        return aiService.generateChatResponse(prompt)
    }
}
