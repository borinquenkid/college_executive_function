package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Adds a new source, extracts deliverables (deadlines, exams, quizzes) for display
 * in the Studio panel, and caches the result. Does NOT push events to the calendar
 * or the AI-generated overlay — that happens only when the user explicitly pushes.
 */
class SourceAdder(
    private val aiService: AIService,
    private val eventGenerationService: EventGenerationService,
    private val contextAgent: ContextAgent,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val cacheRepository: AnalysisCacheRepository,
    private val sourceRepository: SourceRepository,
    private val onEventsAdded: (List<Event>) -> Unit = {},
    private val onError: (AgentError) -> Unit = {}
) {
    private val processingMutex = Mutex()

    fun addSource(source: SourceItem, forceRefresh: Boolean = false) {
        scope.launch {
            if (aiService.isConfigured()) {
                processingMutex.withLock {
                    try {
                        val hash = ContentHasher.hash(source.fragments)
                        processSourceWithCache(source, hash, forceRefresh)
                    } catch (e: Exception) {
                        handleFailure(source, e)
                    }
                }
            }
        }
    }

    private suspend fun processSourceWithCache(source: SourceItem, hash: String, forceRefresh: Boolean) {
        if (!forceRefresh) {
            val cached = cacheRepository.getCached(hash)
            if (cached != null && !isCacheStale(cached)) {
                logger.d("SourceAdder", "Cache hit for source: ${source.title}")
                if (cached.cachedMetadataJson != null) {
                    sourceRepository.updateSourceMetadata(source.title, cached.cachedMetadataJson)
                }
                if (cached.cachedEventsJson.isNotBlank()) {
                    val events = Json.decodeFromString<List<Event>>(cached.cachedEventsJson)
                    onEventsAdded(events)
                }
                return
            }
        }

        logger.d("SourceAdder", "Cache miss or forceRefresh. Extracting source: ${source.title}")
        val allEvents = eventGenerationService.extractDeliverables(source)
        onEventsAdded(allEvents)
        contextAgent.analyzeSource(source)

        val metadataJson = sourceRepository.getSourceMetadata(source.title)
        val eventsJson = Json.encodeToString<List<Event>>(allEvents)
        cacheRepository.putCache(
            CachedAnalysis(
                sourceHash = hash,
                cachedEventsJson = eventsJson,
                cachedMetadataJson = metadataJson,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    private fun isCacheStale(cached: CachedAnalysis): Boolean {
        val ageMs = Clock.System.now().toEpochMilliseconds() - cached.createdAt
        return ageMs > CACHE_TTL_MS
    }

    private fun handleFailure(source: SourceItem, e: Exception) {
        logger.e("SourceAdder", "Failed to process added source: ${source.title}", e)
        if (e.message?.startsWith("QuotaExhausted") == true) {
            onError(AgentError.QuotaExhausted)
        } else {
            onError(AgentError.GenericError(e.message ?: "Unknown error"))
        }
    }

    companion object {
        internal const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
