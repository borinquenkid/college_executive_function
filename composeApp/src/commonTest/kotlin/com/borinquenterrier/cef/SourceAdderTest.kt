package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
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

    "addSource invokes onEventsAdded with AI results when configured" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        val events = listOf(makeTestEvent("AI Event"))
        var eventsAdded: List<Event>? = null
        var errorReceived: AgentError? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns events
        coEvery { contextAgent.analyzeSource(source) } returns Unit

        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository,
            onEventsAdded = { eventsAdded = it }, onError = { errorReceived = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && errorReceived == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        eventsAdded shouldBe events
        errorReceived shouldBe null
    }

    "addSource does not invoke onEventsAdded when AI not configured" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>(relaxed = true)
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns false
        coEvery { cacheRepository.getCached(any()) } returns null

        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(mockk(relaxed = true))

        kotlinx.coroutines.delay(50)

        eventsAdded shouldBe null
    }

    "addSource calls onError with QuotaExhausted when Gemini quota is exceeded" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        var errorReceived: AgentError? = null
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { eventGenerationService.extractDeliverables(source) } throws Exception("QuotaExhausted: daily limit reached")

        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository,
            onEventsAdded = { eventsAdded = it }, onError = { errorReceived = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && errorReceived == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        errorReceived shouldBe AgentError.QuotaExhausted
        eventsAdded shouldBe null
    }

    "addSource calls onError with GenericError on non-quota failure" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        var errorReceived: AgentError? = null
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { eventGenerationService.extractDeliverables(source) } throws Exception("AI timeout")

        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository,
            onEventsAdded = { eventsAdded = it }, onError = { errorReceived = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && errorReceived == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        errorReceived shouldBe AgentError.GenericError("AI timeout")
    }

    "addSource processes concurrent calls sequentially" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val source1 = mockk<SourceItem>(relaxed = true)
        val source2 = mockk<SourceItem>(relaxed = true)
        val log = java.util.concurrent.CopyOnWriteArrayList<String>()

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(any()) } returns null
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { contextAgent.analyzeSource(any()) } returns Unit

        coEvery { eventGenerationService.extractDeliverables(source1) } coAnswers {
            log.add("start-1")
            kotlinx.coroutines.delay(50)
            log.add("end-1")
            listOf()
        }
        coEvery { eventGenerationService.extractDeliverables(source2) } coAnswers {
            log.add("start-2")
            kotlinx.coroutines.delay(50)
            log.add("end-2")
            listOf()
        }

        val testScope = CoroutineScope(Dispatchers.Default)
        val adder = SourceAdder(
            aiService, eventGenerationService, contextAgent, logger, testScope,
            cacheRepository, sourceRepository, onEventsAdded = {}, onError = {}
        )

        adder.addSource(source1)
        adder.addSource(source2)

        var attempts = 0
        while (log.size < 4 && attempts < 30) {
            kotlinx.coroutines.delay(50)
            attempts++
        }

        log.size shouldBe 4
        if (log[0] == "start-1") {
            log.toList() shouldBe listOf("start-1", "end-1", "start-2", "end-2")
        } else {
            log.toList() shouldBe listOf("start-2", "end-2", "start-1", "end-1")
        }
    }

    "addSource handles cache hit by skipping AI and dispatching cached events" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>(relaxed = true)
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)

        val source = mockk<SourceItem>(relaxed = true)
        val sourceTitle = "Test Syllabus"
        coEvery { source.title } returns sourceTitle
        coEvery { source.fragments } returns emptyList()

        val cachedEvents = listOf(
            DayEvent(
                id = "cached-event-1",
                title = "Cached Syllabus Event",
                source = EventSource.AI_GENERATED,
                date = kotlinx.datetime.LocalDate(2026, 6, 16)
            )
        )

        val hash = ContentHasher.hash(emptyList())
        val eventsJson = Json.encodeToString<List<Event>>(cachedEvents)
        val cached = CachedAnalysis(hash, eventsJson, "cached-metadata", Clock.System.now().toEpochMilliseconds())

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns cached

        var eventsAdded: List<Event>? = null
        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        eventsAdded shouldBe cachedEvents
        coVerify(exactly = 0) { eventGenerationService.extractDeliverables(any()) }
        coVerify(exactly = 1) { sourceRepository.updateSourceMetadata(sourceTitle, "cached-metadata") }
    }

    "addSource handles cache miss by invoking AI and writing to cache" {
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
        coEvery { contextAgent.analyzeSource(source) } returns Unit

        var eventsAdded: List<Event>? = null
        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        eventsAdded shouldBe events
        coVerify(exactly = 1) { eventGenerationService.extractDeliverables(source) }
        coVerify(exactly = 1) {
            cacheRepository.putCache(
                match {
                    it.sourceHash == hash &&
                    it.cachedEventsJson.contains("AI Event") &&
                    it.cachedMetadataJson == "new-metadata"
                }
            )
        }
    }

    "addSource with forceRefresh=true bypasses cache and updates cache" {
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
        coEvery { cacheRepository.getCached(hash) } returns mockk() // non-null — should be bypassed
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns events
        coEvery { contextAgent.analyzeSource(source) } returns Unit

        var eventsAdded: List<Event>? = null
        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(source, forceRefresh = true)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        coVerify(exactly = 1) { eventGenerationService.extractDeliverables(source) }
        coVerify(exactly = 1) { cacheRepository.putCache(match { it.sourceHash == hash }) }
    }

    "addSource treats stale cache entry as miss and re-invokes AI" {
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
        // createdAt = 0 (epoch start) → age is many years → stale
        val staleCache = CachedAnalysis(hash, Json.encodeToString<List<Event>>(emptyList()), null, 0L)

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns staleCache
        coEvery { cacheRepository.putCache(any()) } returns Unit
        coEvery { eventGenerationService.extractDeliverables(source) } returns freshEvents
        coEvery { sourceRepository.getSourceMetadata(any()) } returns null
        coEvery { contextAgent.analyzeSource(source) } returns Unit

        var eventsAdded: List<Event>? = null
        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        eventsAdded shouldBe freshEvents
        coVerify(exactly = 1) { eventGenerationService.extractDeliverables(source) }
    }

    "addSource uses fresh cache entry within TTL without calling AI" {
        val aiService = mockk<AIService>()
        val eventGenerationService = mockk<EventGenerationService>(relaxed = true)
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val cacheRepository = mockk<AnalysisCacheRepository>()
        val sourceRepository = mockk<SourceRepository>(relaxed = true)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { source.title } returns "Fresh Syllabus"
        coEvery { source.fragments } returns emptyList()

        val cachedEvents = listOf(makeTestEvent("Cached Event"))
        val hash = ContentHasher.hash(emptyList())
        val eventsJson = Json.encodeToString<List<Event>>(cachedEvents)
        // createdAt = now → age is ~0ms → well within TTL
        val freshCache = CachedAnalysis(hash, eventsJson, null, Clock.System.now().toEpochMilliseconds())

        coEvery { aiService.isConfigured() } returns true
        coEvery { cacheRepository.getCached(hash) } returns freshCache

        var eventsAdded: List<Event>? = null
        val adder = makeAdder(aiService, eventGenerationService, contextAgent, logger,
            cacheRepository, sourceRepository, onEventsAdded = { eventsAdded = it })

        adder.addSource(source)

        var attempts = 0
        while (eventsAdded == null && attempts < 30) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        eventsAdded shouldNotBe null
        eventsAdded shouldBe cachedEvents
        coVerify(exactly = 0) { eventGenerationService.extractDeliverables(any()) }
    }
})
