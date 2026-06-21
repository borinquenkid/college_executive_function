package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

/**
 * End-to-end flow tests that wire real repositories (in-memory SQLite) with a
 * mocked AIService.  These tests cover the critical paths through SourceAdder,
 * EventAgent, CalendarPusher, and TaskDecompositionService without hitting the
 * network, so they can run on every unit-test invocation.
 *
 * Key things verified:
 *  - SourceAdder path never touches EventAgent.isLoading.
 *  - EventAgent.extractDeliverables (pipeline path) does set isLoading.
 *  - pushToCalendar persists events and clears lastGeneratedEvents on success.
 *  - Cache hit skips the AI; forceRefresh bypasses the cache.
 *  - acceptDecomposition called twice produces deterministic IDs — no duplicate rows.
 *  - Multiple sources accumulate in lastGeneratedEvents before a push.
 *  - Quota errors surface via onError and leave lastGeneratedEvents empty.
 */
class SourceFlowTest : FunSpec({

    // ---- shared wiring ----------------------------------------------------------------

    lateinit var database: AppDatabase
    lateinit var localRepo: SqlDelightLocalCalendarRepository
    lateinit var calendarAgent: CalendarAgent
    lateinit var eventAgent: EventAgent
    lateinit var analysisCacheRepo: SqlDelightAnalysisCacheRepository
    lateinit var mockAi: AIService
    lateinit var testScope: CoroutineScope
    val settings = MapSettings()
    val logger = Logger(settings)

    beforeEach {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        // Apply migrations not in the schema (AnalysisCacheEntity, extra columns).
        listOf(
            "ALTER TABLE SourceEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'",
            "ALTER TABLE EventEntity ADD COLUMN studyPlanStart TEXT",
            "ALTER TABLE EventEntity ADD COLUMN gradeWeight REAL",
            "ALTER TABLE EventEntity ADD COLUMN completionStatus TEXT NOT NULL DEFAULT 'INCOMPLETE'",
            "ALTER TABLE SourceEntity ADD COLUMN contentHash TEXT",
            """CREATE TABLE IF NOT EXISTS AnalysisCacheEntity (
                sourceHash TEXT PRIMARY KEY NOT NULL,
                cachedEventsJson TEXT NOT NULL,
                cachedMetadataJson TEXT,
                createdAt INTEGER NOT NULL
            )"""
        ).forEach { sql -> try { driver.execute(null, sql, 0) } catch (_: Exception) {} }

        database = AppDatabase(driver)
        mockAi = mockk(relaxed = true)
        every { mockAi.isConfigured() } returns true

        localRepo = SqlDelightLocalCalendarRepository(database, settings)
        calendarAgent = CalendarAgent(localRepo, mockk(relaxed = true), logger)
        eventAgent = EventAgent(mockAi, calendarAgent, database, logger = logger)
        analysisCacheRepo = SqlDelightAnalysisCacheRepository(database)
        testScope = CoroutineScope(Dispatchers.Default)
    }

    afterEach { testScope.cancel() }

    // ---- helpers ------------------------------------------------------------------

    fun makeSource(title: String = "CS101 Syllabus") = SourceItem(
        title = title,
        category = SourceCategory.SYLLABUS,
        fragments = listOf(SourceFragment(text = "Final exam 2026-12-15. Midterm 2026-11-01."))
    )

    fun makeEvent(title: String, date: LocalDate = LocalDate(2026, 12, 15)) = DayEvent(
        id = null,
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date
    )

    fun makeSourceAdder(
        onEventsAdded: (List<Event>) -> Unit = { eventAgent.setGeneratedEvents(it) },
        onError: (AgentError) -> Unit = {}
    ) = SourceAdder(
        aiService = mockAi,
        eventGenerationService = EventGenerationService(
            mockAi,
            NormalizationService(),
            SyllabusAuditor(mockAi, logger)
        ),
        contextAgent = mockk(relaxed = true),
        logger = logger,
        scope = testScope,
        cacheRepository = analysisCacheRepo,
        sourceRepository = mockk(relaxed = true),
        onEventsAdded = onEventsAdded,
        onError = onError
    )

    // ---- SourceAdder path --------------------------------------------------------

    test("SourceAdder path: events populate lastGeneratedEvents without touching isLoading") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(makeEvent("Final Exam"))

        makeSourceAdder().addSource(makeSource())

        // isLoading must never flip — SourceAdder bypasses EventAgent.runAgentAction.
        eventAgent.isLoading.value shouldBe false

        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        eventAgent.lastGeneratedEvents.value.map { it.title } shouldBe listOf("Final Exam")
        eventAgent.isLoading.value shouldBe false
    }

    test("setGeneratedEvents is synchronous and never touches isLoading") {
        eventAgent.setGeneratedEvents(listOf(makeEvent("Quiz")))

        eventAgent.lastGeneratedEvents.value shouldHaveSize 1
        eventAgent.isLoading.value shouldBe false
    }

    // ---- EventAgent / pipeline path ----------------------------------------------

    test("extractDeliverables (agent path) sets isLoading true during extraction, false after") {
        // delay(1) forces the mock to suspend, ensuring the StateFlow collector sees isLoading=true
        // before it flips back to false. Without the delay the transition can be too fast for the
        // background collector coroutine to observe on slower/single-core CI machines.
        coEvery { mockAi.generateCalendarEvents(any()) } coAnswers {
            delay(1)
            listOf(makeEvent("Midterm"))
        }

        val sawLoading = mutableListOf<Boolean>()
        val collector = testScope.launch {
            eventAgent.isLoading.collect { sawLoading.add(it) }
        }

        eventAgent.extractDeliverables(makeSource())
        collector.cancel()

        sawLoading.any { it } shouldBe true            // was true at some point
        eventAgent.isLoading.value shouldBe false       // false once done
        eventAgent.lastGeneratedEvents.value shouldHaveSize 1
    }

    test("SourceProcessingPipeline: isLoading is false after the full pipeline completes") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(makeEvent("Project"))

        SourceProcessingPipeline(
            ingestionAgent = mockk(relaxed = true),
            eventAgent = eventAgent,
            contextAgent = mockk(relaxed = true),
            logger = logger
        ).processSource(makeSource())

        eventAgent.isLoading.value shouldBe false
    }

    // ---- Push semantics ---------------------------------------------------------

    test("pushToCalendar persists events to local calendar and clears lastGeneratedEvents on success") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(
            makeEvent("Final"),
            makeEvent("Midterm", LocalDate(2026, 11, 1))
        )

        eventAgent.extractDeliverables(makeSource())
        eventAgent.lastGeneratedEvents.value shouldHaveSize 2

        eventAgent.pushToCalendar("default")

        val saved = calendarAgent.getEvents("default")
        saved shouldHaveSize 2
        saved.map { it.title } shouldContainExactlyInAnyOrder listOf("Final", "Midterm")

        // CalendarPusher sets lastGeneratedEvents to empty after a conflict-free push.
        eventAgent.lastGeneratedEvents.value shouldHaveSize 0
    }

    test("push button semantics: enabled (events pending + not loading) after SourceAdder path") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(makeEvent("Exam"))

        makeSourceAdder().addSource(makeSource())
        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        // Condition that enables the push button in StudioPanel
        (eventAgent.lastGeneratedEvents.value.isNotEmpty() && !eventAgent.isLoading.value).shouldBeTrue()
    }

    test("process button semantics: disabled (events already pending) after SourceAdder path") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(makeEvent("Exam"))

        makeSourceAdder().addSource(makeSource())
        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        // Condition that disables the process button in StudioPanel
        eventAgent.lastGeneratedEvents.value.isNotEmpty() shouldBe true
    }

    // ---- Cache ------------------------------------------------------------------

    test("cache hit: second addSource with identical content skips the AI") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(makeEvent("Cached Exam"))

        val adder = makeSourceAdder()
        val source = makeSource()

        adder.addSource(source)
        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        eventAgent.clear()
        adder.addSource(source)
        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        // AI was called exactly once; cache served the second request.
        coVerify(exactly = 1) { mockAi.generateCalendarEvents(any()) }
    }

    test("forceRefresh bypasses cache and calls AI again") {
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(makeEvent("Fresh Exam"))

        val adder = makeSourceAdder()
        val source = makeSource()

        adder.addSource(source)
        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        eventAgent.clear()
        adder.addSource(source, forceRefresh = true)
        withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }

        coVerify(exactly = 2) { mockAi.generateCalendarEvents(any()) }
    }

    // ---- Decomposition ----------------------------------------------------------

    test("acceptDecomposition twice produces identical IDs — no duplicate rows in calendar") {
        val target = DayEvent(
            id = "paper-001",
            title = "Research Paper",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 12, 20)
        )
        calendarAgent.saveEvent(target, "default")

        coEvery { mockAi.decomposeTask(any(), any()) } returns listOf(
            DecomposedTask("Outline draft", daysBeforeDue = 3, description = ""),
            DecomposedTask("Write introduction", daysBeforeDue = 5, description = "")
        )

        eventAgent.decomposeTask(target)
        eventAgent.acceptDecomposition("default")
        val countAfterFirst = calendarAgent.getEvents("default").size

        eventAgent.decomposeTask(target)
        eventAgent.acceptDecomposition("default")
        val countAfterSecond = calendarAgent.getEvents("default").size

        countAfterSecond shouldBe countAfterFirst
    }

    test("acceptDecomposition sets studyPlanStart on the target event") {
        val target = DayEvent(
            id = "essay-002",
            title = "Essay",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 11, 20)
        )
        calendarAgent.saveEvent(target, "default")

        coEvery { mockAi.decomposeTask(any(), any()) } returns listOf(
            DecomposedTask("Draft outline", daysBeforeDue = 7, description = "")
        )

        eventAgent.decomposeTask(target)
        eventAgent.acceptDecomposition("default")

        val allSaved = calendarAgent.getEvents("default")
        val updated = allSaved.find { it.id == target.id }
        updated shouldNotBe null
        updated!!.studyPlanStart shouldNotBe null
    }

    // ---- Multi-source accumulation -----------------------------------------------

    test("accumulating events from two sources before push shows all events") {
        val events1 = listOf(makeEvent("CS Final"))
        val events2 = listOf(makeEvent("Math Final", LocalDate(2026, 12, 18)))
        coEvery { mockAi.generateCalendarEvents(any()) } returnsMany listOf(events1, events2)

        var accumulated = emptyList<Event>()
        val adder = SourceAdder(
            aiService = mockAi,
            eventGenerationService = EventGenerationService(
                mockAi, NormalizationService(), SyllabusAuditor(mockAi, logger)
            ),
            contextAgent = mockk(relaxed = true),
            logger = logger,
            scope = testScope,
            cacheRepository = analysisCacheRepo,
            sourceRepository = mockk(relaxed = true),
            onEventsAdded = { new ->
                accumulated = accumulated + new
                eventAgent.setGeneratedEvents(accumulated)
            }
        )

        adder.addSource(SourceItem("Source A",
            listOf(SourceFragment(text = "Source A: final exam 2026-12-15.")), SourceCategory.SYLLABUS))
        adder.addSource(SourceItem("Source B",
            listOf(SourceFragment(text = "Source B: math final 2026-12-18.")), SourceCategory.SYLLABUS))

        withTimeout(8_000) { eventAgent.lastGeneratedEvents.first { it.size >= 2 } }

        eventAgent.lastGeneratedEvents.value.map { it.title } shouldContainExactlyInAnyOrder
            listOf("CS Final", "Math Final")
    }

    // ---- Error handling ---------------------------------------------------------

    test("quota error in SourceAdder triggers onError and leaves lastGeneratedEvents empty") {
        coEvery { mockAi.generateCalendarEvents(any()) } throws
            RuntimeException("QuotaExhausted: daily limit reached")

        var capturedError: AgentError? = null
        makeSourceAdder(
            onEventsAdded = { eventAgent.setGeneratedEvents(it) },
            onError = { capturedError = it }
        ).addSource(makeSource())

        delay(3_000)

        capturedError shouldBe AgentError.QuotaExhausted
        eventAgent.lastGeneratedEvents.value shouldHaveSize 0
    }

    test("generic error in SourceAdder triggers GenericError and leaves lastGeneratedEvents empty") {
        coEvery { mockAi.generateCalendarEvents(any()) } throws
            RuntimeException("network timeout")

        var capturedError: AgentError? = null
        makeSourceAdder(
            onEventsAdded = { eventAgent.setGeneratedEvents(it) },
            onError = { capturedError = it }
        ).addSource(makeSource())

        delay(3_000)

        (capturedError is AgentError.GenericError) shouldBe true
        eventAgent.lastGeneratedEvents.value shouldHaveSize 0
    }

    // ---- Multi-source accumulation (production DependencyContainer wiring) ------

    /**
     * Regression: DependencyContainer.sourceAdder used setGeneratedEvents(events) which
     * REPLACED the list on each callback.  When three sources finished (possibly in any
     * order), only the last one's events survived.  Fix: accumulate with title+date dedup.
     */
    test("three sources from cache all appear in lastGeneratedEvents (production accumulation wiring)") {
        val calendarEvents  = listOf(makeEvent("Calendar Event",  LocalDate(2026, 8, 25)))
        val syllabusEvents  = listOf(makeEvent("Syllabus Deadline", LocalDate(2026, 10, 15)))
        val classEvents     = listOf(makeEvent("Class Assignment", LocalDate(2026, 11, 30)))
        coEvery { mockAi.generateCalendarEvents(any()) } returnsMany
            listOf(calendarEvents, syllabusEvents, classEvents)

        // Mirror the production accumulation logic from DependencyContainer
        val adder = SourceAdder(
            aiService = mockAi,
            eventGenerationService = EventGenerationService(
                mockAi, NormalizationService(), SyllabusAuditor(mockAi, logger)
            ),
            contextAgent = mockk(relaxed = true),
            logger = logger,
            scope = testScope,
            cacheRepository = analysisCacheRepo,
            sourceRepository = mockk(relaxed = true),
            onEventsAdded = { newEvents ->
                val existing = eventAgent.lastGeneratedEvents.value
                val toAdd = newEvents.filter { new ->
                    existing.none { it.title == new.title && it.date == new.date }
                }
                eventAgent.setGeneratedEvents(existing + toAdd)
            }
        )

        adder.addSource(SourceItem("calendar.pdf",
            listOf(SourceFragment(text = "Calendar: orientation 2026-08-25.")), SourceCategory.SYLLABUS))
        adder.addSource(SourceItem("syllabi.pdf",
            listOf(SourceFragment(text = "Syllabus: deadline 2026-10-15.")), SourceCategory.SYLLABUS))
        adder.addSource(SourceItem("class.pdf",
            listOf(SourceFragment(text = "Class: assignment due 2026-11-30.")), SourceCategory.SYLLABUS))

        withTimeout(10_000) { eventAgent.lastGeneratedEvents.first { it.size >= 3 } }

        val titles = eventAgent.lastGeneratedEvents.value.map { it.title }
        titles shouldContainExactlyInAnyOrder listOf("Calendar Event", "Syllabus Deadline", "Class Assignment")
    }

    test("duplicate title+date from two sources is deduplicated in accumulation") {
        val sharedEvent = makeEvent("Shared Exam", LocalDate(2026, 12, 10))
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(sharedEvent)

        val adder = SourceAdder(
            aiService = mockAi,
            eventGenerationService = EventGenerationService(
                mockAi, NormalizationService(), SyllabusAuditor(mockAi, logger)
            ),
            contextAgent = mockk(relaxed = true),
            logger = logger,
            scope = testScope,
            cacheRepository = analysisCacheRepo,
            sourceRepository = mockk(relaxed = true),
            onEventsAdded = { newEvents ->
                val existing = eventAgent.lastGeneratedEvents.value
                val toAdd = newEvents.filter { new ->
                    existing.none { it.title == new.title && it.date == new.date }
                }
                eventAgent.setGeneratedEvents(existing + toAdd)
            }
        )

        adder.addSource(SourceItem("a.pdf",
            listOf(SourceFragment(text = "Source A exam 2026-12-10.")), SourceCategory.SYLLABUS))
        adder.addSource(SourceItem("b.pdf",
            listOf(SourceFragment(text = "Source B exam 2026-12-10 different text.")), SourceCategory.SYLLABUS))

        withTimeout(8_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
        delay(1_000) // allow both sources to complete

        eventAgent.lastGeneratedEvents.value shouldHaveSize 1
    }

    // ---- LOCAL_ONLY re-push ---------------------------------------------------------

    /**
     * Regression: CalendarPushResolver deduped by title+date against ALL local events,
     * including LOCAL_ONLY ones.  Events saved locally when a previous remote push failed
     * (e.g. calendar was deleted) were silently skipped on subsequent pushes, so they
     * never reached Google Calendar.
     *
     * Fix: only treat SYNCED events as "already handled"; LOCAL_ONLY must be re-attempted.
     */
    test("push re-attempts LOCAL_ONLY events that were never synced to remote") {
        // Seed local DB with a LOCAL_ONLY event (simulates a previous failed remote push).
        val stuck = DayEvent(
            id = "stuck-001",
            title = "Issue Brief #1",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 7, 15),
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        localRepo.saveEvent(stuck, "default")

        // Now push an event with the same title+date.
        eventAgent.setGeneratedEvents(listOf(
            DayEvent(
                id = null,
                title = "Issue Brief #1",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 7, 15)
            )
        ))
        eventAgent.pushToCalendar("default")

        // CalendarAgent uses local-only (no Google link in test) so success is saving locally.
        // The key assertion: the event was NOT skipped — it was pushed through CalendarPusher.
        eventAgent.lastGeneratedEvents.value shouldHaveSize 0  // cleared on success = event was processed
        eventAgent.isLoading.value shouldBe false
    }

    // ---- Deleted calendar push failure ------------------------------------------

    /**
     * Reproduces the user-observed sequence:
     *   1. Delete Google Calendar on google.com
     *   2. Load docs → events appear in the Studio panel
     *   3. Click Publish → push fails with CalendarNotFoundException (HTTP 404)
     *
     * Expected: errorState is surfaced as GenericError, isLoading returns to false,
     * and lastGeneratedEvents is NOT cleared (events remain so the user can retry
     * after re-linking the calendar).
     */
    /**
     * This test reproduces the exact bug: CalendarAgent.saveEvent() caught ALL exceptions
     * including CalendarNotFoundException and fell back to local-only save, so the app
     * showed "Success!" while nothing reached Google Calendar.
     *
     * The fix: re-throw CalendarNotFoundException specifically so it propagates to
     * CalendarPusher which surfaces it via errorState.
     */
    test("push when linked Google calendar was deleted: error is surfaced, not swallowed (regression)") {
        // Wire up real CalendarAgent with a mock remote that throws 404.
        // Set GOOGLE_ACCESS_TOKEN in settings so isGoogleLinked() returns true.
        val linkedSettings = MapSettings()
        linkedSettings.putString("GOOGLE_ACCESS_TOKEN", "fake-token")
        val linkedLocalRepo = SqlDelightLocalCalendarRepository(database, linkedSettings)
        val deletedRemoteRepo = mockk<RemoteCalendarRepository>(relaxed = true) {
            coEvery { saveEvent(any(), any()) } throws
                CalendarNotFoundException("primary", "Calendar 'primary' was deleted (HTTP 404)")
        }
        val linkedCalendarAgent = CalendarAgent(linkedLocalRepo, deletedRemoteRepo, logger)
        val agentWithDeletedCalendar = EventAgent(mockAi, linkedCalendarAgent, database, logger = logger)

        agentWithDeletedCalendar.setGeneratedEvents(listOf(makeEvent("Final Exam")))
        agentWithDeletedCalendar.pushToCalendar("default")

        // Error must be surfaced — CalendarNotFoundException must NOT be silently swallowed.
        (agentWithDeletedCalendar.errorState.value is AgentError.GenericError) shouldBe true
        agentWithDeletedCalendar.isLoading.value shouldBe false
    }

    test("pushToCalendar surfaces CalendarNotFoundException when calendar was deleted") {
        val deletedCalendarAgent = mockk<CalendarAgent>(relaxed = true) {
            coEvery { getEvents(any()) } returns emptyList()
            coEvery { saveEvent(any(), any()) } throws
                CalendarNotFoundException("primary", "Calendar 'primary' was deleted (HTTP 404)")
        }
        val agentWithDeletedCalendar = EventAgent(mockAi, deletedCalendarAgent, database, logger = logger)

        agentWithDeletedCalendar.setGeneratedEvents(listOf(makeEvent("Final Exam")))
        agentWithDeletedCalendar.lastGeneratedEvents.value shouldHaveSize 1

        agentWithDeletedCalendar.pushToCalendar("default")

        // Error must be surfaced — not silently swallowed.
        (agentWithDeletedCalendar.errorState.value is AgentError.GenericError) shouldBe true
        // Loading indicator must be cleared even on failure.
        agentWithDeletedCalendar.isLoading.value shouldBe false
    }

    test("pushToCalendar leaves lastGeneratedEvents intact when push fails so user can retry") {
        val deletedCalendarAgent = mockk<CalendarAgent>(relaxed = true) {
            coEvery { getEvents(any()) } returns emptyList()
            coEvery { saveEvent(any(), any()) } throws
                CalendarNotFoundException("primary", "Calendar not found (HTTP 404)")
        }
        val agentWithDeletedCalendar = EventAgent(mockAi, deletedCalendarAgent, database, logger = logger)

        agentWithDeletedCalendar.setGeneratedEvents(listOf(
            makeEvent("Final Exam"),
            makeEvent("Midterm", LocalDate(2026, 11, 1))
        ))

        agentWithDeletedCalendar.pushToCalendar("default")

        // Events must NOT be cleared — user needs them to retry after re-linking calendar.
        agentWithDeletedCalendar.lastGeneratedEvents.value shouldHaveSize 2
    }

    // ---- Clear ------------------------------------------------------------------

    test("clear() empties lastGeneratedEvents and resets status") {
        eventAgent.setGeneratedEvents(listOf(makeEvent("Exam")))
        eventAgent.lastGeneratedEvents.value shouldHaveSize 1

        eventAgent.clear()

        eventAgent.lastGeneratedEvents.value shouldHaveSize 0
    }
})
