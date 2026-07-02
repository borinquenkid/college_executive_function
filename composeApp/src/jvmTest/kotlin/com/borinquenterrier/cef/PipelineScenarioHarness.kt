package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Headless harness for exercising the REAL ingestion/push/sync/reconcile pipeline against an
 * in-memory DB and a [FakeRemoteCalendar]. Lets a scenario seed any intermediate state (local
 * and/or remote), script the LLM's output (or make it throw/confabulate), run pipeline steps, and
 * assert the resulting local + remote state — no UI, no network, no real cef.db.
 *
 * "Live" mode is on (fake GOOGLE_ACCESS_TOKEN, non-test run_profile) so pushes/syncs actually flow
 * through to the fake remote, the same path production uses.
 */
class PipelineScenarioHarness(
    semesterStart: String? = "2026-06-01",
    semesterEnd: String? = "2026-08-01",
    today: LocalDate = LocalDate(2026, 6, 15),
    // When true, the scripted LLM is wrapped in the real GroundingGuard decorator so scenarios
    // exercise confabulation defense (year-grounding) exactly as production does.
    grounded: Boolean = false
) {
    private val settings = MapSettings().apply { putString("GOOGLE_ACCESS_TOKEN", "fake-token") }
    private val db = createTestDatabase()
    val localRepo = SqlDelightLocalCalendarRepository(db, settings)
    val remote = FakeRemoteCalendar()
    private val sourceRepo = SqlDelightSourceRepository(db)

    private val prefs = StudyPreferences(semesterStart = semesterStart, semesterEnd = semesterEnd)
    private val prefsPort = object : PreferencesPort {
        override suspend fun getPreferences() = prefs
        override suspend fun savePreferences(preferences: StudyPreferences) = Unit
    }

    private val clock: Clock = object : Clock {
        override fun now(): Instant =
            Instant.fromEpochMilliseconds(today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L)
    }

    // Scriptable LLM output for the next ingest.
    private var nextEvents: List<Event> = emptyList()
    private var nextStudyPlan: List<Event> = emptyList()
    private var extractThrows: Throwable? = null
    private val ai = ScriptedAIService(
        onStudyPlan = { nextStudyPlan },
        onCalendarEvents = { extractThrows?.let { throw it }; nextEvents }
    )
    // The AI the pipeline actually calls: optionally wrapped in the production grounding decorator.
    private val pipelineAi: AIService = if (grounded) GroundingGuardAIService(ai) else ai

    val calendarAgent = CalendarAgent(
        localRepo, remote, preferencesRepository = prefsPort,
        sourceRepository = sourceRepo,
        remoteClearDelayFn = {} // no real backoff wait in scenario tests
    )
    private val eventAgent = EventAgent(
        aiService = pipelineAi,
        repository = calendarAgent,
        database = db,
        preferencesRepository = prefsPort,
        clock = clock
    )
    private val contextAgent = ContextAgent(pipelineAi, mockk(relaxed = true), FragmentRanker(), SourceContextBuilder())
    private val pipeline = SourceProcessingPipeline(
        ingestionAgent = mockk(relaxed = true), // processSource takes a ready SourceItem; ingestion is unused
        eventAgent = eventAgent,
        contextAgent = contextAgent,
        logger = Logger(settings)
    )

    // ── seed an intermediate state ────────────────────────────────────────────
    fun seedRemote(events: List<Event>) = remote.seed(events)
    fun seedLocal(events: List<Event>) = runBlocking { events.forEach { localRepo.saveEvent(it) } }
    /** Register source records (so events tagged with them are not treated as orphans). */
    fun seedSources(titles: List<String>) = runBlocking {
        titles.forEach { sourceRepo.saveSource(SourceItem(it, emptyList(), SourceCategory.OTHER), null) }
    }
    fun deleteSourceRecord(title: String) = runBlocking { sourceRepo.deleteSource(title) }

    // ── pipeline steps ────────────────────────────────────────────────────────
    /** Run the auto-push pipeline for a source whose extraction yields [generates]. [sourceText]
     * is the fragment content grounding checks against (years mentioned there are the ground truth). */
    fun ingest(title: String, generates: List<Event>, sourceText: String = "text") = runBlocking {
        // Register the source like production ingestion does, so its events aren't seen as orphans.
        sourceRepo.saveSource(SourceItem(title, emptyList(), SourceCategory.OTHER), null)
        nextEvents = generates
        pipeline.processSource(
            SourceItem(title, listOf(SourceFragment(sourceText, type = SourceType.TEXT)), SourceCategory.OTHER)
        )
    }

    /** Run the pipeline where extraction throws [error] (simulates an LLM failure). */
    fun ingestFailing(title: String, error: Throwable): Throwable? = runBlocking {
        extractThrows = error
        val caught = runCatching {
            pipeline.processSource(SourceItem(title, listOf(SourceFragment("text", type = SourceType.TEXT)), SourceCategory.OTHER))
        }.exceptionOrNull()
        extractThrows = null
        caught
    }

    fun sync() = runBlocking { calendarAgent.synchronize() }
    fun reconcile(): ReconciliationReport = runBlocking { calendarAgent.reconcile() }
    fun repair(report: ReconciliationReport) = runBlocking { calendarAgent.applyReconciliation(report) }
    fun retryLocalOnly() = runBlocking { calendarAgent.retryLocalOnly() }
    fun reset(): Unit = runBlocking { calendarAgent.resetCalendar() }
    fun selfHeal(): ReconciliationReport = runBlocking { calendarAgent.selfHeal() }
    fun checkHealth(): ReconciliationReport = runBlocking { calendarAgent.checkHealth() }
    fun pendingReview(): List<Event> = calendarAgent.pendingReview.value

    // ── assertions ────────────────────────────────────────────────────────────
    /** Active local events (excludes soft-deleted DELETED_LOCALLY tombstones) — what the UI shows. */
    fun localEvents(): List<Event> =
        runBlocking { localRepo.getAllEvents() }.filter { it.syncStatus != SyncStatus.DELETED_LOCALLY }

    fun remoteEvents(): List<Event> = remote.events()
}
