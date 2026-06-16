package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Adds a new source, generates calendar events via AI, and notifies listeners.
 * Handles AI configuration check and error reporting.
 */
class SourceAdder(
    private val aiService: AIService,
    private val contextAgent: ContextAgent,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val cacheRepository: AnalysisCacheRepository,
    private val sourceRepository: SourceRepository,
    private val onEventsAdded: (List<Event>) -> Unit,
    private val onError: (AgentError) -> Unit = {}
) {
    private val json = Json { ignoreUnknownKeys = true }
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
            if (cached != null) {
                logger.d("SourceAdder", "Cache hit for source: ${source.title}")
                val events = json.decodeFromString<List<Event>>(cached.cachedEventsJson)
                onEventsAdded(events)
                if (cached.cachedMetadataJson != null) {
                    sourceRepository.updateSourceMetadata(source.title, cached.cachedMetadataJson)
                }
                return
            }
        }

        logger.d("SourceAdder", "Cache miss or forceRefresh. Processing source: ${source.title}")
        val allEvents = aiService.generateCalendarEvents(source.fragments)
        onEventsAdded(allEvents)
        contextAgent.analyzeSource(source)

        val metadataJson = sourceRepository.getSourceMetadata(source.title)
        val eventsJson = json.encodeToString(allEvents)
        cacheRepository.putCache(
            CachedAnalysis(
                sourceHash = hash,
                cachedEventsJson = eventsJson,
                cachedMetadataJson = metadataJson,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    private fun handleFailure(source: SourceItem, e: Exception) {
        logger.e("SourceAdder", "Failed to process added source: ${source.title}", e)
        if (e.message?.startsWith("QuotaExhausted") == true) {
            onError(AgentError.QuotaExhausted)
        } else {
            onError(AgentError.GenericError(e.message ?: "Unknown error"))
        }
    }
}
