package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SourceAdderTest : StringSpec({

    fun testScope() = CoroutineScope(Dispatchers.Unconfined)

    fun makeTestEvent(title: String) = DayEvent(
        id = "event-id",
        title = title,
        source = EventSource.AI_GENERATED,
        date = kotlinx.datetime.LocalDate(2026, 6, 16)
    )

    fun makeAdder(
        aiService: AIService,
        eventGenerationService: EventGenerationService,
        contextAgent: ContextAgent,
        logger: Logger,
        cacheRepository: AnalysisCacheRepository,
        sourceRepository: SourceRepository,
        onEventsAdded: (List<Event>) -> Unit = {},
        onError: (AgentError) -> Unit = {}
    ) = SourceAdder(
        aiService, eventGenerationService, contextAgent, logger, testScope(),
        cacheRepository, sourceRepository, onEventsAdded, onError
    )

    // ── happy path ──────────────────────────────────────────────────────────

    "addSource calls extractDeliverables and notifies onEventsAdded on cache miss" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        val events = listOf(makeTestEvent("AI Event"))
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns events
        coEvery { contextAgent.analyzeSource(source, any()) } returns Unit
        coEvery { sourceRepository.getSourceMetadata(any()) } returns null

        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) {
            kotlinx.coroutines.delay(10); attempts++
        }

        eventsAdded shouldBe events
        coVerify(exactly = 1) { eventGenerationService.extractDeliverables(source) }
    }

    "addSource also calls contextAgent.analyzeSource for metadata on cache miss" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns emptyList()
        coEvery { contextAgent.analyzeSource(source, any()) } returns Unit
        coEvery { sourceRepository.getSourceMetadata(any()) } returns null

        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository).addSource(source)

        kotlinx.coroutines.delay(50)
        coVerify(exactly = 1) { contextAgent.analyzeSource(source, any()) }
    }

    "addSource does nothing when AI not configured" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>(relaxed = true)
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns false

        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })
            .addSource(mockk(relaxed = true))

        kotlinx.coroutines.delay(50)

        eventsAdded shouldBe null
        coVerify(exactly = 0) { eventGenerationService.extractDeliverables(any()) }
    }

    // ── error handling ──────────────────────────────────────────────────────

    "addSource calls onError with QuotaExhausted when Gemini quota is exceeded" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        var errorReceived: AgentError? = null
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { eventGenerationService.extractDeliverables(source) } throws Exception("QuotaExhausted: daily limit reached")

        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository,
            onEventsAdded = { eventsAdded = it }, onError = { errorReceived = it })
            .addSource(source)

        var attempts = 0
        while (eventsAdded == null && errorReceived == null && attempts < 30) {
            kotlinx.coroutines.delay(10); attempts++
        }

        errorReceived shouldBe AgentError.QuotaExhausted
        eventsAdded shouldBe null
    }

    "addSource calls onError with GenericError on non-quota failure" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        var errorReceived: AgentError? = null
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { eventGenerationService.extractDeliverables(source) } throws Exception("AI timeout")

        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository,
            onEventsAdded = { eventsAdded = it }, onError = { errorReceived = it })
            .addSource(source)

        var attempts = 0
        while (eventsAdded == null && errorReceived == null && attempts < 30) {
            kotlinx.coroutines.delay(10); attempts++
        }

        errorReceived shouldBe AgentError.GenericError("AI timeout")
    }

    // ── concurrency ─────────────────────────────────────────────────────────

    "addSource processes concurrent calls sequentially" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source1 = mockk<SourceItem>(relaxed = true)
        val source2 = mockk<SourceItem>(relaxed = true)
        val log = java.util.concurrent.CopyOnWriteArrayList<String>()

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { contextAgent.analyzeSource(any(), any()) } returns Unit
        coEvery { sourceRepository.getSourceMetadata(any()) } returns null

        coEvery { eventGenerationService.extractDeliverables(source1) } coAnswers {
            log.add("start-1"); kotlinx.coroutines.delay(50); log.add("end-1"); listOf()
        }
        coEvery { eventGenerationService.extractDeliverables(source2) } coAnswers {
            log.add("start-2"); kotlinx.coroutines.delay(50); log.add("end-2"); listOf()
        }

        val adder = SourceAdder(
            aiService, eventGenerationService, contextAgent, logger,
            CoroutineScope(Dispatchers.Default), cacheRepository, sourceRepository
        )
        adder.addSource(source1)
        adder.addSource(source2)

        var attempts = 0
        while (log.size < 4 && attempts < 30) { kotlinx.coroutines.delay(50); attempts++ }

        log.size shouldBe 4
        if (log[0] == "start-1") {
            log.toList() shouldBe listOf("start-1", "end-1", "start-2", "end-2")
        } else {
            log.toList() shouldBe listOf("start-2", "end-2", "start-1", "end-1")
        }
    }

    // ── cache ───────────────────────────────────────────────────────────────

    "addSource uses cache hit to dispatch cached events and restore metadata without calling AI" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>(relaxed = true)
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        val sourceTitle = "Test Syllabus"
        coEvery { source.title } returns sourceTitle
        coEvery { source.fragments } returns emptyList()

        val cachedEvents = listOf(makeTestEvent("Cached Syllabus Event"))
        val hash = ContentHasher.hash(emptyList())
        val eventsJson = Json.encodeToString<List<Event>>(cachedEvents)
        val cached = CachedAnalysis(hash, eventsJson, "cached-metadata", Clock.System.now().toEpochMilliseconds())

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns cached

        var eventsAdded: List<Event>? = null
        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })
            .addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) { kotlinx.coroutines.delay(10); attempts++ }

        eventsAdded shouldBe cachedEvents
        coVerify(exactly = 0) { eventGenerationService.extractDeliverables(any()) }
        coVerify(exactly = 1) { sourceRepository.updateSourceMetadata(sourceTitle, "cached-metadata") }
    }

    "addSource on cache miss writes events and metadata to cache" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        val sourceTitle = "Test Syllabus"
        coEvery { source.title } returns sourceTitle
        coEvery { source.fragments } returns emptyList()

        val events = listOf(makeTestEvent("AI Event"))
        val hash = ContentHasher.hash(emptyList())

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns null
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns events
        coEvery { sourceRepository.getSourceMetadata(sourceTitle) } returns "new-metadata"
        coEvery { contextAgent.analyzeSource(source, any()) } returns Unit

        var eventsAdded: List<Event>? = null
        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })
            .addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) { kotlinx.coroutines.delay(10); attempts++ }

        eventsAdded shouldBe events
        coVerify(exactly = 1) {
            cacheRepository.putCache(match {
                it.sourceHash == hash &&
                it.cachedEventsJson.contains("AI Event") &&
                it.cachedMetadataJson == "new-metadata"
            })
        }
    }

    "addSource with forceRefresh bypasses cache and re-extracts" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        coEvery { source.title } returns "Syllabus"
        coEvery { source.fragments } returns emptyList()

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns mockk()
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns emptyList()
        coEvery { contextAgent.analyzeSource(source, any()) } returns Unit
        coEvery { sourceRepository.getSourceMetadata(any()) } returns null

        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository).addSource(source, forceRefresh = true)

        kotlinx.coroutines.delay(50)
        coVerify(exactly = 1) { eventGenerationService.extractDeliverables(source) }
        coVerify(exactly = 1) { cacheRepository.putCache(any()) }
    }

    "addSource treats stale cache as miss and re-extracts" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        coEvery { source.title } returns "Old Syllabus"
        coEvery { source.fragments } returns emptyList()

        val hash = ContentHasher.hash(emptyList())
        val freshEvents = listOf(makeTestEvent("Fresh Event"))
        val staleCache = CachedAnalysis(hash, Json.encodeToString<List<Event>>(emptyList()), null, 0L)

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns staleCache
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns freshEvents
        coEvery { sourceRepository.getSourceMetadata(any()) } returns null
        coEvery { contextAgent.analyzeSource(source, any()) } returns Unit

        var eventsAdded: List<Event>? = null
        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })
            .addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) { kotlinx.coroutines.delay(10); attempts++ }

        eventsAdded shouldBe freshEvents
        coVerify(exactly = 1) { eventGenerationService.extractDeliverables(source) }
    }

    "addSource uses fresh cache without calling AI" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>(relaxed = true)
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        coEvery { source.title } returns "Fresh Syllabus"
        coEvery { source.fragments } returns emptyList()

        val cachedEvents = listOf(makeTestEvent("Cached Event"))
        val hash = ContentHasher.hash(emptyList())
        val eventsJson = Json.encodeToString<List<Event>>(cachedEvents)
        val freshCache = CachedAnalysis(hash, eventsJson, null, Clock.System.now().toEpochMilliseconds())

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns freshCache

        var eventsAdded: List<Event>? = null
        makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })
            .addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) { kotlinx.coroutines.delay(10); attempts++ }

        eventsAdded shouldNotBe null
        eventsAdded shouldBe cachedEvents
        coVerify(exactly = 0) { eventGenerationService.extractDeliverables(any()) }
    }
})
