package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
 * Parametric matrix test for the source-to-calendar pipeline.
 *
 * Each section covers one seam in the pipeline and enumerates all meaningful
 * input/output combinations.  Real in-memory SQLite is used throughout;
 * only the AI and remote repository are mocked.
 *
 * Pipeline:
 *   Source → [Cache] → AI → Accumulation → [Push filter] → ConflictResolver
 *     → CalendarAgent → [remote routing] → SyncStatus
 *
 * Note on lifecycle: Kotest's top-level `beforeEach` does not reliably apply to
 * `withData`-generated tests inside `context` blocks (a known Kotest quirk).
 * Each `context` block that uses `withData` declares its own `beforeEach { resetState() }`.
 */
class PipelineMatrixTest : FunSpec({

    // ── shared wiring ─────────────────────────────────────────────────────────

    lateinit var database: AppDatabase
    lateinit var localRepo: SqlDelightLocalCalendarRepository
    lateinit var calendarAgent: CalendarAgent
    lateinit var eventAgent: EventAgent
    lateinit var analysisCacheRepo: SqlDelightAnalysisCacheRepository
    lateinit var mockAi: AIService
    lateinit var testScope: CoroutineScope
    val settings = MapSettings()
    val logger = Logger(settings)

    fun freshDriver(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
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
        return driver
    }

    fun resetState() {
        database = AppDatabase(freshDriver())
        mockAi = mockk(relaxed = true)
        every { mockAi.isConfigured() } returns true
        localRepo = SqlDelightLocalCalendarRepository(database, settings)
        calendarAgent = CalendarAgent(localRepo, mockk(relaxed = true), logger)
        eventAgent = EventAgent(mockAi, calendarAgent, database, logger = logger)
        analysisCacheRepo = SqlDelightAnalysisCacheRepository(database)
        testScope = CoroutineScope(Dispatchers.Default)
    }

    beforeEach { resetState() }
    afterEach { testScope.cancel() }

    fun day(title: String, date: LocalDate = LocalDate(2026, 12, 15)) = DayEvent(
        title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, date = date
    )

    fun source(title: String, text: String = "Final exam 2026-12-15.") = SourceItem(
        title = title, category = SourceCategory.SYLLABUS,
        fragments = listOf(SourceFragment(text = text))
    )

    fun adder(onEventsAdded: (List<Event>) -> Unit = { eventAgent.setGeneratedEvents(it) }) =
        SourceAdder(
            aiService = mockAi,
            eventGenerationService = EventGenerationService(
                mockAi, NormalizationService(), SyllabusAuditor(mockAi, logger)
            ),
            contextAgent = mockk(relaxed = true),
            logger = logger,
            scope = testScope,
            cacheRepository = analysisCacheRepo,
            sourceRepository = mockk(relaxed = true),
            onEventsAdded = onEventsAdded,
            onError = { eventAgent.reportError(it) }
        )

    fun accumulatingAdder() = adder { newEvents ->
        val existing = eventAgent.lastGeneratedEvents.value
        val toAdd = newEvents.filter { new ->
            existing.none { it.title == new.title && it.date == new.date }
        }
        eventAgent.setGeneratedEvents(existing + toAdd)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 1 — Source input
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 1: Source input") {
        listOf(
            Triple("single source → one AI call", 1, 1),
            Triple("two sources with distinct content → two AI calls", 2, 2),
            Triple("three sources with distinct content → three AI calls", 3, 3),
        ).forEach { (label, sourceCount, expectedCalls) ->
            test(label) {
                var aiCallCount = 0
                coEvery { mockAi.generateCalendarEvents(any()) } answers {
                    listOf(day("Event $aiCallCount", LocalDate(2026, 12, 15 + aiCallCount++)))
                }

                val a = accumulatingAdder()
                repeat(sourceCount) { i ->
                    a.addSource(source("Source $i", "Unique text for source $i final 2026-12-${15 + i}."))
                }

                withTimeout(10_000) {
                    eventAgent.lastGeneratedEvents.first { it.size >= sourceCount }
                }
                coVerify(exactly = expectedCalls) { mockAi.generateCalendarEvents(any()) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 2 — Cache layer
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 2: Cache layer") {

        test("cache miss → AI called on first add") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Exam"))
            adder().addSource(source("S"))
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            coVerify(exactly = 1) { mockAi.generateCalendarEvents(any()) }
        }

        test("cache hit → AI skipped on identical second add") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Exam"))
            val a = adder()
            val s = source("S")
            a.addSource(s)
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            eventAgent.clear()
            a.addSource(s)
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            coVerify(exactly = 1) { mockAi.generateCalendarEvents(any()) }
        }

        test("force refresh bypasses cache → AI called twice") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Exam"))
            val a = adder()
            val s = source("S")
            a.addSource(s)
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            eventAgent.clear()
            a.addSource(s, forceRefresh = true)
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            coVerify(exactly = 2) { mockAi.generateCalendarEvents(any()) }
        }

        test("two sources with identical fragments share one AI call (same hash)") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Exam"))
            val a = adder()
            a.addSource(source("Title A"))
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            eventAgent.clear()
            a.addSource(source("Title B")) // same fragment text → same hash → cache hit
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            coVerify(exactly = 1) { mockAi.generateCalendarEvents(any()) }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 3 — AI extraction outcomes
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 3: AI extraction outcomes") {

        test("AI returns events → lastGeneratedEvents populated") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns
                listOf(day("Final"), day("Midterm", LocalDate(2026, 11, 1)))
            eventAgent.extractDeliverables(source("S"))
            eventAgent.lastGeneratedEvents.value shouldHaveSize 2
        }

        test("AI returns empty list → lastGeneratedEvents empty, no error") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns emptyList()
            eventAgent.extractDeliverables(source("S"))
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
            eventAgent.errorState.value shouldBe null
        }

        test("quota error → QuotaExhausted surfaced in errorState") {
            coEvery { mockAi.generateCalendarEvents(any()) } throws
                RuntimeException("QuotaExhausted: daily limit reached")
            eventAgent.extractDeliverables(source("S"))
            eventAgent.errorState.value shouldBe AgentError.QuotaExhausted
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
        }

        // runAgentAction sets statusMessage (not errorState) for non-quota errors.
        // Only CalendarNotFoundException and quota errors surface via errorState.
        test("generic AI error → statusMessage starts with 'Error:', errorState stays null") {
            coEvery { mockAi.generateCalendarEvents(any()) } throws
                RuntimeException("network timeout")
            eventAgent.extractDeliverables(source("S"))
            eventAgent.statusMessage.value.startsWith("Error:") shouldBe true
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
            eventAgent.errorState.value shouldBe null
        }

        test("isLoading is true during extraction, false after") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Exam"))
            val loading = mutableListOf<Boolean>()
            val job = testScope.launch { eventAgent.isLoading.collect { loading.add(it) } }
            eventAgent.extractDeliverables(source("S"))
            job.cancel()
            loading.any { it } shouldBe true
            eventAgent.isLoading.value shouldBe false
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 4 — Multi-source accumulation
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 4: Multi-source accumulation") {

        test("replace wiring loses events when second source finishes (demonstrates the bug)") {
            val events1 = listOf(day("CS Final", LocalDate(2026, 12, 15)))
            val events2 = listOf(day("Math Final", LocalDate(2026, 12, 18)))
            coEvery { mockAi.generateCalendarEvents(any()) } returnsMany listOf(events1, events2)

            // WRONG wiring: replaces each time (what DependencyContainer used to do)
            val a = adder { eventAgent.setGeneratedEvents(it) }
            a.addSource(source("CS", "CS final 2026-12-15."))
            a.addSource(source("Math", "Math final 2026-12-18."))
            withTimeout(8_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            delay(500)

            // Only one of the two sources survives — the last to finish wins
            eventAgent.lastGeneratedEvents.value shouldHaveSize 1
        }

        test("accumulate wiring keeps all events from multiple sources") {
            val events1 = listOf(day("CS Final", LocalDate(2026, 12, 15)))
            val events2 = listOf(day("Math Final", LocalDate(2026, 12, 18)))
            coEvery { mockAi.generateCalendarEvents(any()) } returnsMany listOf(events1, events2)

            val a = accumulatingAdder()
            a.addSource(source("CS", "CS final 2026-12-15."))
            a.addSource(source("Math", "Math final 2026-12-18."))
            withTimeout(8_000) { eventAgent.lastGeneratedEvents.first { it.size >= 2 } }

            eventAgent.lastGeneratedEvents.value shouldHaveSize 2
        }

        test("duplicate title+date from two sources is deduplicated") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns
                listOf(day("Shared Exam", LocalDate(2026, 12, 10)))
            val a = accumulatingAdder()
            a.addSource(source("A", "Source A exam 2026-12-10."))
            a.addSource(source("B", "Source B different text 2026-12-10."))
            withTimeout(8_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            delay(1_000)
            eventAgent.lastGeneratedEvents.value shouldHaveSize 1
        }

        test("SourceAdder path never sets isLoading") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Exam"))
            accumulatingAdder().addSource(source("S"))
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            eventAgent.isLoading.value shouldBe false
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 5 — Push date filter
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 5: Push date filter") {
        data class PushFilterCase(
            val label: String,
            val events: List<Event>,
            val expectedEventsCleared: Boolean,
            val expectedStatusContains: String
        )

        listOf(
            PushFilterCase(
                "no events in lastGeneratedEvents → push is a no-op, default status unchanged",
                emptyList(),
                expectedEventsCleared = false,
                expectedStatusContains = "Select a source"
            ),
            PushFilterCase(
                "all past events → status set to 'No future events', events cleared",
                listOf(day("Old Exam", LocalDate(2020, 1, 1))),
                expectedEventsCleared = true,
                expectedStatusContains = "No future events"
            ),
            PushFilterCase(
                "all future events → push succeeds, events cleared",
                listOf(day("Future Exam", LocalDate(2026, 12, 15))),
                expectedEventsCleared = true,
                expectedStatusContains = "Success"
            ),
            PushFilterCase(
                "mixed past+future → only future pushed, events cleared",
                listOf(
                    day("Past", LocalDate(2020, 1, 1)),
                    day("Future", LocalDate(2026, 12, 15))
                ),
                expectedEventsCleared = true,
                expectedStatusContains = "Success"
            ),
        ).forEach { (label, events, expectedCleared, expectedStatus) ->
            test(label) {
                // For "no events" case, do NOT call setGeneratedEvents — leave at empty default
                // so statusMessage stays at "Select a source and an action." (calling
                // setGeneratedEvents(emptyList()) would mutate it to "0 events ready to sync.").
                if (events.isNotEmpty()) {
                    eventAgent.setGeneratedEvents(events)
                }
                eventAgent.pushToCalendar("default")

                if (expectedCleared) {
                    eventAgent.lastGeneratedEvents.value shouldHaveSize 0
                }
                eventAgent.statusMessage.value.contains(expectedStatus, ignoreCase = true) shouldBe true
                eventAgent.isLoading.value shouldBe false
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 6 — Conflict resolution / dedup
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 6: Conflict resolution and dedup") {

        test("SYNCED local event with same title+date is skipped — no duplicate") {
            localRepo.saveEvent(
                DayEvent(
                    id = "ex-001", title = "Final", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 12, 15),
                    syncStatus = SyncStatus.SYNCED
                ), "default"
            )
            eventAgent.setGeneratedEvents(listOf(day("Final", LocalDate(2026, 12, 15))))
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 1
        }

        test("LOCAL_ONLY local event with same title+date is re-pushed — stale purged, no duplicate") {
            localRepo.saveEvent(
                DayEvent(
                    id = "lo-001", title = "Issue Brief", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 15),
                    syncStatus = SyncStatus.LOCAL_ONLY
                ), "default"
            )
            eventAgent.setGeneratedEvents(listOf(
                DayEvent(title = "Issue Brief", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 15))
            ))
            eventAgent.pushToCalendar("default")
            // Stale LOCAL_ONLY row purged before re-save → exactly 1, not 2
            calendarAgent.getEvents("default") shouldHaveSize 1
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
            eventAgent.isLoading.value shouldBe false
        }

        test("different title, same date → both pushed, no dedup") {
            eventAgent.setGeneratedEvents(listOf(
                day("Exam A", LocalDate(2026, 12, 15)),
                day("Exam B", LocalDate(2026, 12, 15))
            ))
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 2
        }

        test("same title, different dates → both pushed, no dedup") {
            eventAgent.setGeneratedEvents(listOf(
                day("Midterm", LocalDate(2026, 10, 15)),
                day("Midterm", LocalDate(2026, 11, 15))
            ))
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 2
        }

        // DayEvent.overlaps() always returns false, so two identical DayEvents in the
        // same batch both pass CollisionResolver. Intra-batch dedup for DayEvents is not
        // currently implemented — only dedup against existing SYNCED events.
        test("exact duplicate in same push batch → both saved (DayEvent intra-batch dedup not implemented)") {
            val e = day("Final", LocalDate(2026, 12, 15))
            eventAgent.setGeneratedEvents(listOf(e, e.copy()))
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 2
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 7 — Remote routing (CalendarAgent.saveEvent)
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 7: Remote routing") {

        test("not Google-linked → saves locally, no error") {
            eventAgent.setGeneratedEvents(listOf(day("Exam")))
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 1
            eventAgent.errorState.value shouldBe null
        }

        test("Google-linked + CalendarNotFoundException → error surfaced, not swallowed") {
            val ls = MapSettings().also { it.putString("GOOGLE_ACCESS_TOKEN", "tok") }
            val lr = SqlDelightLocalCalendarRepository(database, ls)
            val remote = mockk<RemoteCalendarRepository>(relaxed = true) {
                coEvery { saveEvent(any(), any()) } throws
                    CalendarNotFoundException("primary", "Deleted (HTTP 404)")
            }
            val agent = EventAgent(mockAi, CalendarAgent(lr, remote, logger), database, logger = logger)
            agent.setGeneratedEvents(listOf(day("Exam")))
            agent.pushToCalendar("default")
            (agent.errorState.value is AgentError.GenericError) shouldBe true
            agent.isLoading.value shouldBe false
        }

        test("Google-linked + network error → LOCAL_ONLY fallback, no user-facing error") {
            val ls = MapSettings().also { it.putString("GOOGLE_ACCESS_TOKEN", "tok") }
            val lr = SqlDelightLocalCalendarRepository(database, ls)
            val remote = mockk<RemoteCalendarRepository>(relaxed = true) {
                coEvery { saveEvent(any(), any()) } throws java.io.IOException("timeout")
            }
            val agent = EventAgent(mockAi, CalendarAgent(lr, remote, logger), database, logger = logger)
            agent.setGeneratedEvents(listOf(day("Exam")))
            agent.pushToCalendar("default")
            lr.getAllEvents("default") shouldHaveSize 1
            agent.errorState.value shouldBe null
            agent.lastGeneratedEvents.value shouldHaveSize 0
        }

        test("Google-linked + remote success → saved as SYNCED") {
            val ls = MapSettings().also { it.putString("GOOGLE_ACCESS_TOKEN", "tok") }
            val lr = SqlDelightLocalCalendarRepository(database, ls)
            val agent = EventAgent(mockAi, CalendarAgent(lr, mockk(relaxed = true), logger), database, logger = logger)
            agent.setGeneratedEvents(listOf(day("Exam")))
            agent.pushToCalendar("default")
            lr.getAllEvents("default").first().syncStatus shouldBe SyncStatus.SYNCED
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STAGE 8 — LOCAL_ONLY re-push
    // ══════════════════════════════════════════════════════════════════════════

    context("Stage 8: LOCAL_ONLY re-push on subsequent push") {

        test("LOCAL_ONLY event is not filtered — it is re-attempted, stale row purged") {
            localRepo.saveEvent(
                DayEvent(
                    id = "lo-001", title = "Stuck Event", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1),
                    syncStatus = SyncStatus.LOCAL_ONLY
                ), "default"
            )
            eventAgent.setGeneratedEvents(listOf(
                DayEvent(title = "Stuck Event", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1))
            ))
            eventAgent.pushToCalendar("default")
            // Stale LOCAL_ONLY purged before re-save → exactly 1 (not 2)
            calendarAgent.getEvents("default") shouldHaveSize 1
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
        }

        test("SYNCED event IS filtered — no re-push, no duplicate") {
            localRepo.saveEvent(
                DayEvent(
                    id = "sy-001", title = "Already Synced", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1),
                    syncStatus = SyncStatus.SYNCED
                ), "default"
            )
            eventAgent.setGeneratedEvents(listOf(
                DayEvent(title = "Already Synced", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1))
            ))
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 1 // no duplicate
        }

        test("mix of LOCAL_ONLY and SYNCED → LOCAL_ONLY stale purged + re-saved, SYNCED skipped, no duplicates") {
            localRepo.saveEvent(
                DayEvent(id = "sy-001", title = "Synced Event", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, syncStatus = SyncStatus.SYNCED,
                    date = LocalDate(2026, 9, 1)), "default"
            )
            localRepo.saveEvent(
                DayEvent(id = "lo-001", title = "Local Only Event", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, syncStatus = SyncStatus.LOCAL_ONLY,
                    date = LocalDate(2026, 9, 2)), "default"
            )
            eventAgent.setGeneratedEvents(listOf(
                DayEvent(title = "Synced Event", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1)),
                DayEvent(title = "Local Only Event", source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 2))
            ))
            eventAgent.pushToCalendar("default")
            // Synced Event: skipped by Phase 2 dedup. Local Only Event: stale purged + new saved.
            // Total: 1 (Synced) + 1 (re-pushed Local Only) = 2
            calendarAgent.getEvents("default") shouldHaveSize 2
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // END-TO-END — Full pipeline scenarios
    // ══════════════════════════════════════════════════════════════════════════

    context("End-to-end: Full pipeline") {

        test("happy path: source → AI → accumulate → push → local calendar populated") {
            coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(day("Final Exam"))
            accumulatingAdder().addSource(source("Syllabus"))
            withTimeout(5_000) { eventAgent.lastGeneratedEvents.first { it.isNotEmpty() } }
            eventAgent.isLoading.value shouldBe false

            eventAgent.pushToCalendar("default")

            calendarAgent.getEvents("default") shouldHaveSize 1
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
            eventAgent.errorState.value shouldBe null
        }

        test("quota error: no events accumulated, error surfaced, publish button stays hidden") {
            coEvery { mockAi.generateCalendarEvents(any()) } throws
                RuntimeException("QuotaExhausted: 429")
            eventAgent.extractDeliverables(source("Syllabus"))
            eventAgent.errorState.value shouldBe AgentError.QuotaExhausted
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
            // Publish button condition: (events.isNotEmpty && !isLoading) → false when no events
            (eventAgent.lastGeneratedEvents.value.isNotEmpty() && !eventAgent.isLoading.value) shouldBe false
        }

        test("three sources, all cached, accumulate → push all to calendar") {
            val allEvents = listOf(
                day("CS Final", LocalDate(2026, 12, 15)),
                day("Math Final", LocalDate(2026, 12, 18)),
                day("Orientation", LocalDate(2026, 8, 25))
            )
            coEvery { mockAi.generateCalendarEvents(any()) } returnsMany allEvents.map { listOf(it) }
            val a = accumulatingAdder()
            a.addSource(source("CS", "CS final 2026-12-15."))
            a.addSource(source("Math", "Math final 2026-12-18."))
            a.addSource(source("Cal", "Orientation 2026-08-25."))
            withTimeout(10_000) { eventAgent.lastGeneratedEvents.first { it.size >= 3 } }
            eventAgent.pushToCalendar("default")
            calendarAgent.getEvents("default") shouldHaveSize 3
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
        }

        test("push fails (CalendarNotFoundException): error set, events preserved for retry") {
            val deletedAgent = mockk<CalendarAgent>(relaxed = true) {
                coEvery { getEvents(any()) } returns emptyList()
                coEvery { saveEvent(any(), any()) } throws
                    CalendarNotFoundException("primary", "Calendar deleted (HTTP 404)")
                coEvery { hardDeleteLocalOnly(any(), any()) } returns Unit
            }
            val agent = EventAgent(mockAi, deletedAgent, database, logger = logger)
            agent.setGeneratedEvents(listOf(day("Final"), day("Midterm", LocalDate(2026, 11, 1))))
            agent.pushToCalendar("default")
            (agent.errorState.value is AgentError.GenericError) shouldBe true
            agent.lastGeneratedEvents.value shouldHaveSize 2 // preserved for retry
            agent.isLoading.value shouldBe false
        }

        test("class events stuck as LOCAL_ONLY are re-pushed without duplicates (regression for the class-events-not-syncing bug)") {
            // Prior run: class events saved LOCAL_ONLY because calendar was deleted
            listOf(
                "Issue Brief #1" to LocalDate(2026, 7, 10),
                "Issue Brief #2" to LocalDate(2026, 7, 24),
                "Final Paper"   to LocalDate(2026, 8, 7)
            ).forEach { (title, date) ->
                localRepo.saveEvent(
                    DayEvent(id = "id-$title", title = title, source = EventSource.AI_GENERATED,
                        category = AcademicCategory.DEADLINE, syncStatus = SyncStatus.LOCAL_ONLY,
                        date = date), "default"
                )
            }

            // Current run: user loaded docs again, events appear in lastGeneratedEvents
            eventAgent.setGeneratedEvents(listOf(
                DayEvent(title = "Issue Brief #1", source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 10)),
                DayEvent(title = "Issue Brief #2", source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 24)),
                DayEvent(title = "Final Paper",    source = EventSource.AI_GENERATED, category = AcademicCategory.DEADLINE, date = LocalDate(2026, 8, 7))
            ))
            eventAgent.pushToCalendar("default")

            // Stale LOCAL_ONLY rows purged, 3 new rows saved = exactly 3 (not 6)
            calendarAgent.getEvents("default") shouldHaveSize 3
            eventAgent.lastGeneratedEvents.value shouldHaveSize 0
            eventAgent.errorState.value shouldBe null
        }

        test("demo scenario: blank calendar, load 3 docs, push all to remote Google Calendar") {
            // Blank calendar: nothing pre-seeded, Google account linked.
            val ls = MapSettings().also { it.putString("GOOGLE_ACCESS_TOKEN", "tok") }
            val lr = SqlDelightLocalCalendarRepository(database, ls)
            val savedRemotely = mutableListOf<Event>()
            val remote = mockk<RemoteCalendarRepository>(relaxed = true) {
                coEvery { saveEvent(any(), any()) } answers {
                    savedRemotely.add(firstArg())
                }
            }
            val agent = EventAgent(mockAi, CalendarAgent(lr, remote, logger), database, logger = logger)

            // Load 3 docs: each produces one event
            val events = listOf(
                day("CS Final Exam",       LocalDate(2026, 12, 15)),
                day("Math Midterm",        LocalDate(2026, 11, 10)),
                day("History Essay Due",   LocalDate(2026, 12, 1))
            )
            coEvery { mockAi.generateCalendarEvents(any()) } returnsMany events.map { listOf(it) }

            val a = SourceAdder(
                aiService = mockAi,
                eventGenerationService = EventGenerationService(mockAi, NormalizationService(), SyllabusAuditor(mockAi, logger)),
                contextAgent = mockk(relaxed = true),
                logger = logger,
                scope = testScope,
                cacheRepository = analysisCacheRepo,
                sourceRepository = mockk(relaxed = true),
                onEventsAdded = { newEvents ->
                    val existing = agent.lastGeneratedEvents.value
                    val toAdd = newEvents.filter { new -> existing.none { it.title == new.title && it.date == new.date } }
                    agent.setGeneratedEvents(existing + toAdd)
                },
                onError = { agent.reportError(it) }
            )
            a.addSource(source("CS Syllabus",      "CS final 2026-12-15."))
            a.addSource(source("Math Syllabus",    "Math midterm 2026-11-10."))
            a.addSource(source("History Syllabus", "History essay 2026-12-01."))

            withTimeout(10_000) { agent.lastGeneratedEvents.first { it.size >= 3 } }
            agent.isLoading.value shouldBe false // publish button should be enabled

            // Push to calendar
            agent.pushToCalendar("default")

            // All 3 events reached the remote calendar
            savedRemotely shouldHaveSize 3
            // All 3 saved locally as SYNCED
            lr.getAllEvents("default").all { it.syncStatus == SyncStatus.SYNCED } shouldBe true
            // lastGeneratedEvents cleared (nothing left to push)
            agent.lastGeneratedEvents.value shouldHaveSize 0
            agent.errorState.value shouldBe null
        }
    }
})
