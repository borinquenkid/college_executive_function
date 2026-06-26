package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
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
    private val onError: (AgentError) -> Unit = {},
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp
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

    private fun semesterFilter(prefs: StudyPreferences): Pair<LocalDate, LocalDate>? {
        val start = prefs.semesterStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = prefs.semesterEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return if (start != null && end != null) start to end else null
    }

    private fun applyFilter(events: List<Event>, filter: Pair<LocalDate, LocalDate>?): List<Event> {
        if (filter == null) return events
        return events.filter { EventDeduplicator.dateOf(it) in filter.first..filter.second }
    }

    private suspend fun processSourceWithCache(source: SourceItem, hash: String, forceRefresh: Boolean) {
        val filter = semesterFilter(preferencesRepository.getPreferences())
        AppTracer.current.span(
            "sourceadder.process",
            mapOf(
                "source.title" to source.title,
                "source.hash" to hash.take(16),
                "force_refresh" to forceRefresh.toString()
            )
        ) {
            if (!forceRefresh) {
                val cached = cacheRepository.getCached(hash)
                if (cached != null && !isCacheStale(cached)) {
                    val hasEvents = cached.cachedEventsJson.isNotBlank()
                    logger.d("SourceAdder", "Cache hit for source: ${source.title}")
                    addEvent(
                        "sourceadder.cache.hit",
                        mapOf(
                            "has_events" to hasEvents.toString(),
                            "has_metadata" to (cached.cachedMetadataJson != null).toString()
                        )
                    )
                    if (cached.cachedMetadataJson != null) {
                        sourceRepository.updateSourceMetadata(source.title, cached.cachedMetadataJson)
                    }
                    if (hasEvents) {
                        val events = applyFilter(
                            Json.decodeFromString<List<Event>>(cached.cachedEventsJson),
                            filter
                        )
                        setAttribute("cache.events", events.size.toLong())
                        onEventsAdded(events)
                    }
                    return@span
                }
            }

            addEvent("sourceadder.cache.miss", mapOf("force_refresh" to forceRefresh.toString()))
            logger.d("SourceAdder", "Cache miss or forceRefresh. Extracting source: ${source.title}")
            val allEvents = eventGenerationService.extractDeliverables(source)
            val filteredEvents = applyFilter(allEvents, filter)
            setAttribute("extracted.events", filteredEvents.size.toLong())
            onEventsAdded(filteredEvents)
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
