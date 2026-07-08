package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-pipeline persistence tests for STLCC source material: ingest -> extract -> push ->
 * synchronize -> assert on what actually lands in the local calendar repository.
 *
 * [StlccIntegrationTest] only asserts on `eventAgent.lastGeneratedEvents.value` right after
 * extraction — it never calls `pushToCalendar()` or `synchronize()`, so it cannot catch bugs in
 * the persistence layer. Running the real JVM app against the real STLCC PDFs (ingest a formal
 * syllabus, an academic calendar, then a weekly class schedule, in sequence) surfaced two
 * persistence bugs that StlccIntegrationTest's green run completely missed:
 *
 *   1. `CalendarPusher` dropped every event dated before "today" before it was ever saved,
 *      contradicting the Confabulation Gate mandate (AGENTS.md) that students may load syllabi
 *      from any semester and every grounded event must reach the calendar regardless of today's
 *      date.
 *   2. `SyncNegotiator` misclassified a just-synced event as remote-deleted when the remote
 *      list fetch hadn't caught up yet (Google Calendar's list endpoint has no read-after-write
 *      guarantee), and hard-deleted it locally — wiping earlier sources' events when a later
 *      source's processing triggered a sync.
 *
 * ## Running
 *   ./gradlew :composeApp:jvmTest --tests "com.borinquenterrier.cef.StlccCalendarPersistenceIntegrationTest" -PrunAITests=true
 */
class StlccCalendarPersistenceIntegrationTest : FunSpec({

    // Mid-semester reference point (STLCC's summer 2026 term runs Jun-Aug). Fixed so "today"
    // deterministically falls AFTER some deliverable dates (e.g. Issue Brief #1 due Jul 1, its
    // draft due Jun 28) and BEFORE others (e.g. Final Paper due Jul 31) — reproducing the exact
    // scenario a student loading their syllabus mid-semester hits. Mutable so the test can also
    // advance past the sync grace period without a real wall-clock wait.
    class MutableFixedClock(startMillis: Long) : Clock {
        var millis: Long = startMillis
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    val midSemesterClock = MutableFixedClock(
        LocalDate(2026, 7, 8).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L
    )

    fun findContributionsDir(): File? = listOf(
        File("contributions"),
        File("../contributions"),
        File("composeApp/src/commonTest/resources/contributions"),
        File("../composeApp/src/commonTest/resources/contributions"),
    ).firstOrNull { it.exists() && it.isDirectory }

    /**
     * Fake remote calendar that simulates Google Calendar's eventual consistency: a just-saved
     * event does not appear in [getAllEvents] until [settle] is called. This reproduces the race
     * a real background harness poll can hit, deterministically and without depending on real
     * network timing.
     */
    class LaggyFakeRemoteCalendarRepository : RemoteCalendarRepository {
        private val settled = mutableMapOf<String, Event>()
        private val pending = mutableMapOf<String, Event>()

        /** Makes every pending write visible in the next [getAllEvents] call. */
        fun settle() {
            settled.putAll(pending)
            pending.clear()
        }

        /** Simulates a genuine remote-side deletion (e.g. the user deleted it in Google Calendar). */
        fun simulateRemoteDeletion(id: String) {
            settled.remove(id)
            pending.remove(id)
        }

        override fun getSettings(): com.russhwolf.settings.Settings? = null

        override suspend fun getAllEvents(calendarId: String): List<Event> = settled.values.toList()

        override suspend fun saveEvent(event: Event, calendarId: String) {
            val id = requireNotNull(event.id) { "Event must have an id before reaching the remote repo" }
            pending[id] = event
        }

        override suspend fun updateEvent(event: Event, calendarId: String) {
            val id = requireNotNull(event.id) { "Event must have an id before reaching the remote repo" }
            settled[id] = event
        }

        override suspend fun deleteEvent(eventId: String, calendarId: String) {
            settled.remove(eventId)
            pending.remove(eventId)
        }

        override suspend fun hardDeleteEvent(eventId: String, calendarId: String) {
            settled.remove(eventId)
            pending.remove(eventId)
        }

        override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> =
            listOf(RemoteCalendarMetadata("default", "default"))

        override suspend fun clearCalendar(calendarId: String) {
            settled.clear()
            pending.clear()
        }

        override suspend fun getEventsInRange(start: LocalDate, end: LocalDate, calendarId: String): List<Event> =
            settled.values.filter { event ->
                val date = when (event) { is DayEvent -> event.date; is TimeEvent -> event.date }
                date >= start && date <= end
            }

        override suspend fun getEventsBySyncStatus(status: SyncStatus, calendarId: String): List<Event> =
            settled.values.filter { it.syncStatus == status }

        override suspend fun getIncompleteEventsBefore(date: LocalDate, calendarId: String): List<Event> = emptyList()

        override suspend fun clearLocalCalendar(calendarId: String) = Unit
    }

    test("STLCC full pipeline: extraction -> push -> sync persists events across sources and dates").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds
    ) {
        val apiKey = resolveApiKey("STLCC full pipeline persistence") ?: return@config
        val contributionsDir = findContributionsDir() ?: run {
            println("SKIPPING: No contributions/ directory found."); return@config
        }

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings().apply {
            putString("CEF_GEMINI_API_KEY", apiKey)
            // SyncGate.isLive() requires a non-blank access token and run_profile != "test" so
            // pushToCalendar() actually exercises the remote leg instead of silently falling back
            // to a local-only save (which would never touch SyncNegotiator at all).
            putString("GOOGLE_ACCESS_TOKEN", "fake-token-for-test")
        }
        val logger = Logger(settings)
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash", Clock.System.now().toEpochMilliseconds()
        )

        val aiService: AIService = GroundingGuardAIService(
            CriticActorAIService(RealAIService(settings, logger, database), logger),
            logger
        )
        val localCalendarRepo = SqlDelightLocalCalendarRepository(database, settings)
        val remoteCalendarRepo = LaggyFakeRemoteCalendarRepository()
        val calendarAgent = CalendarAgent(
            localCalendarRepo,
            remoteCalendarRepo,
            logger = logger,
            clock = midSemesterClock
        )
        val sourceRepository = SqlDelightSourceRepository(database)
        val ingestionAgent = IngestionAgent(
            fileReader = LocalFileReader(),
            docxReader = mockk(relaxed = true),
            pdfReader = PdfReader(),
            webReader = mockk(relaxed = true),
            aiService = aiService,
            sourceRepository = sourceRepository
        )
        val eventAgent = EventAgent(aiService, calendarAgent, database, logger = logger, clock = midSemesterClock)

        suspend fun ingestExtractPush(entry: ContributionIndex): Boolean {
            val pdfFile = File(contributionsDir, entry.relativePath)
            if (!pdfFile.exists()) {
                println("SKIPPING ${entry.name}: not found at ${pdfFile.canonicalPath}")
                return false
            }
            println("\n=== Ingesting ${entry.name} ===")
            val source = skipIfQuotaExhausted("ingest:${pdfFile.name}") {
                ingestionAgent.addLocalFile(pdfFile.absolutePath)
            }
            skipIfQuotaExhausted("extract:${pdfFile.name}") {
                eventAgent.extractDeliverables(source)
            }
            if (eventAgent.errorState.value == AgentError.QuotaExhausted) {
                println("SKIPPING: quota exhausted during extraction of ${entry.name}")
                return false
            }
            println("Extracted ${eventAgent.lastGeneratedEvents.value.size} events from ${entry.name}")
            eventAgent.pushToCalendar("default")
            return true
        }

        // ── 1. Formal syllabus (institutional dates, all in August — future relative to the
        //      mid-semester clock) ─────────────────────────────────────────────────────────
        if (!ingestExtractPush(ContributionIndex.STLCC_ENG101_SYLLABUS)) { driver.close(); return@config }
        val syllabusEventIds = localCalendarRepo.getAllEvents("default").mapNotNull { it.id }.toSet()
        withClue("Formal syllabus should have produced at least one persisted event") {
            syllabusEventIds.isEmpty() shouldBe false
        }

        // ── 2. Reproduce Bug 2: a background harness sync races the just-pushed syllabus
        //      events before the (fake) remote has caught up. Before the fix, these get
        //      misclassified as remote-deleted and hard-deleted locally. ─────────────────────
        calendarAgent.synchronize("default")
        val afterRaceSync = localCalendarRepo.getAllEvents("default").mapNotNull { it.id }.toSet()
        withClue(
            "A just-synced event must survive a synchronize() call even if the remote list " +
                "fetch hasn't caught up yet (simulates Google Calendar's eventual consistency). " +
                "Lost ids: ${syllabusEventIds - afterRaceSync}"
        ) {
            afterRaceSync shouldBe syllabusEventIds
        }

        // Let the fake remote "catch up", then confirm a genuinely-deleted-elsewhere event (old
        // enough to be past the grace period) IS still correctly pruned — the fix must not simply
        // disable deletion detection altogether.
        remoteCalendarRepo.settle()
        val targetId = syllabusEventIds.first()
        midSemesterClock.millis += 6 * 60 * 1000L // advance past the 5-minute grace period
        remoteCalendarRepo.simulateRemoteDeletion(targetId)
        calendarAgent.synchronize("default")
        withClue("A genuinely remote-deleted, grace-period-expired event should still be pruned locally") {
            localCalendarRepo.getAllEvents("default").mapNotNull { it.id } shouldNotContain targetId
        }

        // ── 3. Weekly schedule (spans Jun 8 - Jul 31; "today" is Jul 8, so several
        //      deliverables — Issue Brief #1 due Jul 1, its Jun 28 draft — are in the PAST
        //      relative to the clock). Reproduces Bug 1. ──────────────────────────────────
        if (!ingestExtractPush(ContributionIndex.STLCC_ENG101_WEEKLY)) { driver.close(); return@config }

        val allPersisted = localCalendarRepo.getAllEvents("default")
        fun persistedOn(titleFragment: String, date: LocalDate): Boolean = allPersisted.any { e ->
            titleFragment.lowercase() in e.title.lowercase() &&
                when (e) { is DayEvent -> e.date; is TimeEvent -> e.date } == date
        }

        withClue(
            "Issue Brief #1 (due Jul 1, BEFORE the Jul 8 clock) must be persisted — a past-dated, " +
                "correctly-extracted deliverable must not be silently dropped by the push/sync layer.\n" +
                "All persisted: ${allPersisted.map { "${it.date} ${it.title}" }}"
        ) {
            persistedOn("issue brief #1", LocalDate(2026, 7, 1)) shouldBe true
        }
        withClue("Issue Brief #1 draft (due Jun 28, well before the clock) must also be persisted") {
            persistedOn("issue brief #1", LocalDate(2026, 6, 28)) shouldBe true
        }

        // ── 4. Everything from the earlier syllabus source (except the one event we
        //      deliberately simulated as remote-deleted) must still be there — proves the
        //      multi-source run doesn't wipe earlier sources' events. ───────────────────────
        val survivingSyllabusIds = syllabusEventIds - targetId
        val stillPersisted = allPersisted.mapNotNull { it.id }.toSet()
        withClue(
            "All formal-syllabus events (except the deliberately-simulated remote deletion) must " +
                "still be present after a later source is processed. Missing: ${survivingSyllabusIds - stillPersisted}"
        ) {
            (survivingSyllabusIds - stillPersisted).isEmpty() shouldBe true
        }

        driver.close()
    }
})
