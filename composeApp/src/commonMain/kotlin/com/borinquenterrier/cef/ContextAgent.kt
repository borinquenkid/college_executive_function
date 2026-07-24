package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Lightweight facade coordinating document intelligence services.
 * Delegates to specialized services for fragment ranking and context building.
 */
class ContextAgent(
    private val aiService: AIService,
    private val sourceRepository: SourceRepository,
    private val fragmentRanker: FragmentRanker,
    private val contextBuilder: SourceContextBuilder,
    private val logger: Logger? = null,
    // Nullable + defaulted so existing call sites/tests built without a repository are unaffected —
    // they keep the pre-Part-B behavior (raw history, naive takeLast truncation in ChatBuilder).
    private val chatRepository: ChatRepository? = null,
    // Seam so tests can exercise the compaction trigger with a small window instead of Gemini's
    // real ~1M-token ceiling, which no reasonably-sized test conversation would ever exceed.
    private val contextWindowProvider: () -> Int = { ModelContextWindow.conservativeChatWindow() },
    // Cross-term memory (ADR 0004 / ROADMAP Phase 13, XM-4). Nullable + defaulted for the same
    // reason chatRepository is: existing call sites/tests built without one are unaffected and
    // keep today's behavior (no student-profile block injected).
    private val termProfileRepository: TermProfileRepository? = null
) {
    private val tag = "ContextAgent"

    companion object {
        // Below this many completed terms, no distilled profile is surfaced (ADR 0004's
        // confabulation guardrail — a single term of sparse data is not "recurring" anything).
        const val MIN_TERMS_FOR_PROFILE = 2
    }

    // Serializes the read-plan-summarize-persist critical section in compactHistory per agent
    // instance. Without this, two rapid sends in the same conversation (the UI does not disable
    // input while awaiting a response) could race: both read the same prior summary/boundary,
    // both compute a plan, and the loser's write could regress the boundary or clobber a newer
    // summary with a stale one. Mirrors AppController's sourceProcessingMutex pattern.
    private val compactionMutex = Mutex()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    /**
     * Returns true if analysis completed (or was skipped as already-done) without the underlying
     * AI call failing, false if it failed. A `false` here is not "nothing useful found" — an
     * absent/empty result is a normal, successful outcome — it specifically means the call itself
     * errored (caught below) and was silently absorbed rather than propagated, so callers that care
     * (e.g. [SourceProcessingPipeline]) have a way to notice without this method throwing and
     * breaking every other caller's fire-and-forget usage.
     */
    suspend fun analyzeSource(source: SourceItem, force: Boolean = false): Boolean {
        // analyzeDocument is an LLM call — expensive. Skip it when this source already has
        // metadata so the interactive-add path and the harness pipeline don't both pay for it
        // (idempotent across paths). Explicit re-analysis passes force = true.
        if (!force && !sourceRepository.getSourceMetadata(source.title).isNullOrBlank()) {
            logger?.d(tag, "Skipping re-analysis of ${source.title}; metadata already present")
            return true
        }
        _isAnalyzing.value = true
        return try {
            AppTracer.current.span("context.analyze_source", mapOf("source.title_hash" to TelemetryIdHasher.hash(source.title))) {
                val fullText = source.fragments.joinToString("\n\n") { it.text }
                val metadataJson = aiService.analyzeDocument(fullText)

                if (metadataJson != null) {
                    sourceRepository.updateSourceMetadata(source.title, metadataJson)
                    logger?.d(tag, "Successfully analyzed and persisted metadata for ${source.title}")
                }
                setAttribute("context.metadata_extracted", (metadataJson != null).toString())
            }
            true
        } catch (e: Exception) {
            logger?.e(tag, "Failed to analyze source ${source.title}", e)
            false
        } finally {
            _isAnalyzing.value = false
        }
    }

    suspend fun getSourceMetadata(sourceId: String): String? {
        return sourceRepository.getSourceMetadata(sourceId)
    }

    suspend fun querySource(source: SourceItem, question: String): String {
        val metadata = getSourceMetadata(source.title) ?: ""
        val fragments =
            source.fragments.joinToString("\n\n") { "Page ${it.pageNumber ?: ""}: ${it.text}" }

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
        question: String,
        warnings: List<String> = emptyList(),
        conversationId: String = ChatMessage.DEFAULT_CONVERSATION_ID
    ): String {
        if (sources.isEmpty()) {
            return "No sources are loaded yet. Please add a syllabus or document from the Sources panel first."
        }

        val topPairs = fragmentRanker.rankFragments(sources, question, topK = 15)

        val sourceBlocks = contextBuilder.buildContextBlocks(topPairs) { sourceId ->
            getSourceMetadata(sourceId)
        }

        val studentProfile = studentProfileSummary()

        val compaction = compactHistory(
            conversationId, conversationHistory, sourceBlocks, question,
            profileTokens = studentProfile?.let { TokenEstimator.estimate(it) } ?: 0
        )

        val historyPairs = compaction.verbatimTail
            .map { (if (it.role == ChatRole.USER) "User" else "AI") to it.content }
        val prompt = AiPrompts.getMultiSourceChatPrompt(
            sourceBlocks, historyPairs, question, warnings, compaction.summary,
            // Budgeting ran whenever a chatRepository is wired, whether or not this particular
            // turn actually folded anything — a long-but-under-budget history must not be
            // re-truncated to MAX_HISTORY_TURNS by ChatBuilder's legacy fallback.
            historyAlreadyBudgeted = chatRepository != null,
            studentProfile = studentProfile
        )

        logger?.d(
            tag,
            "queryAllSources: ${sources.size} source(s), ${conversationHistory.size} history turns, " +
                "using top ${topPairs.size} fragments" + (if (compaction.folded) ", folded older turns into summary" else "")
        )
        return aiService.generateChatResponse(prompt)
    }

    /**
     * Cross-term memory read path (ADR 0004 / ROADMAP Phase 13, XM-4). Returns null whenever
     * [termProfileRepository] isn't wired, or the student has fewer than [MIN_TERMS_FOR_PROFILE]
     * completed terms recorded — the confabulation guardrail, enforced here rather than trusting
     * callers to check it themselves. Not RAG-retrieved: one small distilled block, not a corpus
     * to search, computed fresh each call from [TermProfileAggregator.summarize] (plain
     * formatting, no LLM call).
     */
    private suspend fun studentProfileSummary(): String? {
        val repository = termProfileRepository ?: return null
        val profiles = repository.getAll()
        if (profiles.size < MIN_TERMS_FOR_PROFILE) return null
        return TermProfileAggregator.summarize(profiles).takeIf { it.isNotBlank() }
    }

    private data class CompactionOutcome(
        val summary: String?,
        val verbatimTail: List<ChatMessage>,
        val folded: Boolean
    )

    /**
     * Per-turn budget allocation + lazy rolling-summary compaction (design 2.1, Part B). Without a
     * [chatRepository], there is no persisted summary to read/write, so history passes through
     * unchanged and [ChatBuilder]'s own `takeLast(MAX_HISTORY_TURNS)` cut is what applies.
     *
     * Serialized by [compactionMutex] so two overlapping calls for the same conversation (the UI
     * does not block a second send while the first is in flight) can't race on the read-plan-
     * summarize-persist sequence and clobber each other's summary/boundary update.
     */
    private suspend fun compactHistory(
        conversationId: String,
        conversationHistory: List<ChatMessage>,
        sourceBlocks: List<SourceContextBlock>,
        question: String,
        profileTokens: Int = 0
    ): CompactionOutcome = compactionMutex.withLock {
        val repo = chatRepository
            ?: return@withLock CompactionOutcome(summary = null, verbatimTail = conversationHistory, folded = false)

        val conversation = repo.getConversation(conversationId)
        val existingSummary = conversation?.summary

        // The boundary is the id of the newest folded message, not its timestamp: conversationHistory
        // is always the full, totally-ordered history (createdAt ASC, id ASC), so locating the
        // boundary by id and slicing everything after it is exact — a value-threshold comparison on
        // createdAt could misclassify two messages that share a millisecond timestamp across the
        // fold boundary. An unknown/missing boundary id (shouldn't happen given full history is
        // always passed) fails open to "nothing folded yet" rather than silently dropping messages.
        val boundaryIndex = conversation?.summarizedThroughMessageId
            ?.let { id -> conversationHistory.indexOfFirst { it.id == id } }
            ?: -1
        val unsummarized =
            if (boundaryIndex >= 0) conversationHistory.subList(boundaryIndex + 1, conversationHistory.size)
            else conversationHistory

        val sourceBlocksTokens = sourceBlocks.sumOf {
            TokenEstimator.estimate(it.fragmentText.take(ChatBuilder.MAX_CHARS_PER_SOURCE)) +
                TokenEstimator.estimate(it.metadata.orEmpty())
        }
        val budget = ChatBudgetAllocator.historyBudget(
            contextWindow = contextWindowProvider(),
            sourceBlocksTokens = sourceBlocksTokens,
            summaryTokens = TokenEstimator.estimate(existingSummary.orEmpty()),
            questionTokens = TokenEstimator.estimate(question),
            profileTokens = profileTokens
        )

        val plan = ChatCompactionPlanner.plan(unsummarized, budget)
        if (plan.turnsToFold.isEmpty()) {
            return@withLock CompactionOutcome(existingSummary, plan.verbatimTail, folded = false)
        }

        val toFoldPairs = plan.turnsToFold
            .map { (if (it.role == ChatRole.USER) "User" else "AI") to it.content }
        val summaryPrompt = AiPrompts.getConversationSummaryPrompt(existingSummary, toFoldPairs)
        val updatedSummary = aiService.generateChatResponse(summaryPrompt)

        // generateChatResponse swallows its own failures into an "Error: ..." string rather than
        // throwing, and a safety-filtered/empty completion can succeed with blank text — neither
        // should ever corrupt the persisted summary or advance the fold boundary. Fall back to the
        // budget-sized verbatimTail (NOT the raw unsummarized list, which is over budget by
        // definition — that's why folding was triggered).
        if (updatedSummary.isBlank() || updatedSummary.startsWith("Error:")) {
            logger?.e(tag, "Summarization call failed or returned blank for $conversationId; keeping prior summary")
            return@withLock CompactionOutcome(existingSummary, plan.verbatimTail, folded = false)
        }

        val newBoundaryId = plan.turnsToFold.last().id
        runCatching {
            repo.updateSummary(conversationId, updatedSummary, newBoundaryId, Clock.System.now().toEpochMilliseconds())
        }.onFailure {
            logger?.e(tag, "Failed to persist rolling summary for $conversationId", it)
        }

        CompactionOutcome(updatedSummary, plan.verbatimTail, folded = true)
    }
}
