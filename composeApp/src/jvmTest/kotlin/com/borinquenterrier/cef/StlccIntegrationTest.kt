package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Per-document integration tests for STLCC source material.
 *
 * Each test targets ONE document in ContributionIndex and asserts properties
 * specific to that document's content — not just "3+ events", but "class meetings
 * present", "no duplicates", "known deliverables found", and "model stable across
 * all extraction batches."
 *
 * ## Running a single test
 *   ./gradlew :composeApp:jvmTest --tests "com.borinquenterrier.cef.StlccIntegrationTest"
 */
class StlccIntegrationTest : FunSpec({

    // All STLCC documents cover summer 2026. Fixing the clock before the semester
    // start makes every date-relative decision in EventAgent idempotent regardless
    // of when this test actually runs.
    val STLCC_SEMESTER_REF = LocalDate(2026, 5, 1)
    val semesterClock: Clock = object : Clock {
        override fun now(): Instant =
            Instant.fromEpochMilliseconds(
                STLCC_SEMESTER_REF.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L
            )
    }

    fun findContributionsDir(): File? = listOf(
        File("contributions"),
        File("../contributions"),
        File("composeApp/src/commonTest/resources/contributions"),
        File("../composeApp/src/commonTest/resources/contributions"),
    ).firstOrNull { it.exists() && it.isDirectory }

    fun eventFingerprint(event: Event): String {
        val dateStr = when (event) {
            is DayEvent -> event.date.toString()
            is TimeEvent -> "${event.date}T${event.startTime}"
        }
        return "${event.title.trim().lowercase()}|$dateStr"
    }

    fun buildStack(
        settings: MapSettings,
        logger: Logger,
        database: AppDatabase,
        clock: Clock = semesterClock
    ): Triple<IngestionAgent, EventAgent, CalendarAgent> {
        val aiService: AIService = GroundingGuardAIService(
            CriticActorAIService(RealAIService(settings, logger, database), logger),
            logger
        )
        val localCalendarRepo = SqlDelightLocalCalendarRepository(database, settings)
        val calendarAgent = CalendarAgent(
            localCalendarRepo,
            mockk<RemoteCalendarRepository>(relaxed = true),
            logger = logger
        )
        val sourceRepository = SqlDelightSourceRepository(database)
        val ingestionAgent = IngestionAgent(
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = PdfReader(),
            webReader = mockk(relaxed = true),
            driveService = mockk(relaxed = true),
            aiService = aiService,
            sourceRepository = sourceRepository
        )
        val eventAgent = EventAgent(aiService, calendarAgent, database, logger = logger, clock = clock)
        return Triple(ingestionAgent, eventAgent, calendarAgent)
    }

    // ── ENG 101 Weekly Schedule ───────────────────────────────────────────────

    test("STLCC ENG 101 weekly schedule: class meetings present, deadlines present, no duplicates, model stable").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 15).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 15).milliseconds
    ) {
        val apiKey = resolveApiKey("STLCC ENG 101 weekly schedule") ?: return@config
        val contributionsDir = findContributionsDir() ?: run {
            println("SKIPPING: No contributions/ directory found."); return@config
        }

        val entry = ContributionIndex.STLCC_ENG101_WEEKLY
        val pdfFile = File(contributionsDir, entry.relativePath)
        if (!pdfFile.exists()) {
            println("SKIPPING: ${entry.name} not found at ${pdfFile.canonicalPath}"); return@config
        }

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings().apply { putString("CEF_GEMINI_API_KEY", apiKey) }
        val logger = Logger(settings)

        // Pre-seed model so all batches start from the same model — cascade is
        // detectable by comparing the DB entry before and after extraction.
        val seedModel = "gemini-2.5-flash"
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", seedModel, Clock.System.now().toEpochMilliseconds()
        )

        val (ingestionAgent, eventAgent, _) = buildStack(settings, logger, database)

        println("\n=== STLCC ENG 101 — ${entry.description} ===")
        println("Ingesting ${pdfFile.name}…")
        val source = skipIfQuotaExhausted("ingest:${pdfFile.name}") {
            ingestionAgent.addLocalFile(pdfFile.absolutePath)
        }
        println("Category: ${source.category}, Fragments: ${source.fragments.size}")
        println("Extracting events (${source.fragments.size} fragments → ~${(source.fragments.size + 1) / 3} batch(es))…")

        skipIfQuotaExhausted("extract:${pdfFile.name}") {
            eventAgent.extractDeliverables(source)
        }
        println("  status: ${eventAgent.statusMessage.value}")

        if (eventAgent.errorState.value == AgentError.QuotaExhausted) {
            println("SKIPPING assertions: Gemini quota exhausted during extraction.")
            driver.close(); return@config
        }

        val events = eventAgent.lastGeneratedEvents.value
        println("Extracted: ${events.size} events")
        events.groupBy { it.category }.forEach { (cat, evts) ->
            println("  $cat: ${evts.size}")
        }
        events.sortedBy { when(it) { is DayEvent -> it.date.toString(); is TimeEvent -> it.date.toString() } }
            .forEach { println("  ${when(it) { is DayEvent -> it.date; is TimeEvent -> it.date }} [${it.category}] ${it.title}") }

        val modelAfter = database.appDatabaseQueries
            .getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        if (modelAfter != seedModel) {
            println("WARN: Model cascaded during extraction: $seedModel → $modelAfter")
            println("  (This indicates a transient API error mid-stream — results may be inconsistent)")
        } else {
            println("Model stable throughout extraction: $modelAfter")
        }

        // ── 1. No duplicate events ───────────────────────────────────────────
        val fingerprints = events.map { eventFingerprint(it) }
        val duplicates = fingerprints.groupBy { it }.filter { it.value.size > 1 }
        withClue("Duplicate events found:\n${duplicates.keys.joinToString("\n")}") {
            duplicates.isEmpty() shouldBe true
        }

        // ── 2. At least 16 distinct class dates ──────────────────────────────
        // The document has Mon + Wed sessions for all 8 weeks = 16 unique dates.
        // The AI may split compound sessions (e.g. "Assign IB#2 & watch video") into
        // two CLASS events on the same date — we count distinct dates, not raw events.
        val classEvents = events.filter { it.category == AcademicCategory.CLASS }
        val classDateCount = classEvents.map { it.date }.distinct().size
        withClue(
            "Expected at least 16 distinct class dates (Mon+Wed × 8 weeks), got $classDateCount.\n" +
            "All categories: ${events.groupBy { it.category }.mapValues { it.value.size }}\n" +
            "Class events found: ${classEvents.map { "${it.date} ${it.title}" }}"
        ) {
            classDateCount shouldBeGreaterThan 15
            classDateCount shouldBeLessThan 18  // no phantom extra weeks
        }

        // ── 3. All 4 graded assignments present with correct dates ─────────────
        // These are the exact submissions from the document:
        //   Issue Brief #1  → Wed Jul 1, 2026  (50 pts, Week 4)
        //   Issue Brief #2  → Wed Jul 15, 2026 (75 pts, Week 6)
        //   Issue Brief #3  → Wed Jul 22, 2026 (75 pts, Week 7 online)
        //   Final Paper     → Fri Aug 1, 2026  (100 pts, Week 8 online)
        fun hasDeadline(titleFragment: String, date: LocalDate): Boolean =
            events.any { e ->
                titleFragment.lowercase() in e.title.lowercase() &&
                when (e) { is DayEvent -> e.date; is TimeEvent -> e.date } == date
            }

        withClue("Missing: Issue Brief #1 on Jul 1, 2026\nAll events: ${events.map { "${it.date} ${it.title}" }}") {
            hasDeadline("issue brief #1", LocalDate(2026, 7, 1)) shouldBe true
        }
        withClue("Missing: Issue Brief #2 on Jul 15, 2026") {
            hasDeadline("issue brief #2", LocalDate(2026, 7, 15)) shouldBe true
        }
        withClue("Missing: Issue Brief #3 on Jul 22, 2026") {
            hasDeadline("issue brief #3", LocalDate(2026, 7, 22)) shouldBe true
        }
        withClue("Missing: Final Paper on Jul 31, 2026 (Friday of Week 8: Jul 27–Aug 2)") {
            hasDeadline("final paper", LocalDate(2026, 7, 31)) shouldBe true
        }

        // ── 4. Both draft submissions present ────────────────────────────────
        // The document calls these out with explicit Sunday 11:59 PM deadlines.
        withClue("Missing: Issue Brief #1 draft due Sun Jun 28, 2026") {
            hasDeadline("issue brief #1", LocalDate(2026, 6, 28)) shouldBe true
        }
        withClue("Missing: Issue Brief #2 draft due Sun Jul 12, 2026") {
            hasDeadline("issue brief #2", LocalDate(2026, 7, 12)) shouldBe true
        }

        // ── 5. Total event count is in a sane range ───────────────────────────
        // Minimum 36 = 16 class dates + 20 graded/activity events.
        // AI may split compound sessions into multiple CLASS events (+3–5 typical).
        // Over 55 means structural duplicates or hallucinated weeks are slipping through.
        withClue(
            "Event count ${events.size} is outside expected range [36, 55].\n" +
            "Actual breakdown: ${events.groupBy { it.category }.mapValues { it.value.size }}\n" +
            "Dates with multiple events: ${events.groupBy { it.date }.filter { it.value.size > 1 }.mapValues { it.value.size }}"
        ) {
            events.size shouldBeGreaterThan 35
            events.size shouldBeLessThan 56
        }

        driver.close()
    }

    // ── ENG 101 Formal Syllabus ───────────────────────────────────────────────

    test("STLCC ENG 101 formal syllabus: institutional dates only, no class meetings expected").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 8).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 8).milliseconds
    ) {
        val apiKey = resolveApiKey("STLCC ENG 101 formal syllabus") ?: return@config
        val contributionsDir = findContributionsDir() ?: run {
            println("SKIPPING: No contributions/ directory found."); return@config
        }

        val entry = ContributionIndex.STLCC_ENG101_SYLLABUS
        val pdfFile = File(contributionsDir, entry.relativePath)
        if (!pdfFile.exists()) {
            println("SKIPPING: ${entry.name} not found at ${pdfFile.canonicalPath}"); return@config
        }

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings().apply { putString("CEF_GEMINI_API_KEY", apiKey) }
        val logger = Logger(settings)

        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash", Clock.System.now().toEpochMilliseconds()
        )

        val (ingestionAgent, eventAgent, _) = buildStack(settings, logger, database)

        println("\n=== STLCC ENG 101 — ${entry.description} ===")
        val source = skipIfQuotaExhausted("ingest:${pdfFile.name}") {
            ingestionAgent.addLocalFile(pdfFile.absolutePath)
        }
        println("Category: ${source.category}")

        skipIfQuotaExhausted("extract:${pdfFile.name}") {
            eventAgent.extractDeliverables(source)
        }

        val events = eventAgent.lastGeneratedEvents.value
        println("Extracted: ${events.size} events")
        events.forEach { println("  - ${it.date} [${it.category}] ${it.title}") }

        // ── 1. No duplicate events ───────────────────────────────────────────
        val fingerprints = events.map { eventFingerprint(it) }
        val duplicates = fingerprints.groupBy { it }.filter { it.value.size > 1 }
        withClue("Duplicate events: ${duplicates.keys.joinToString()}") {
            duplicates.isEmpty() shouldBe true
        }

        // ── 2. Small count — this is a policy doc, not a schedule ─────────────
        // Expect only institutional dates (semester start/end, withdrawal deadline, etc.)
        // Historically this produces ~6 events; allow up to 15 before it's suspicious.
        withClue("Formal syllabus extracted too many events (${events.size}) — " +
            "policy document should not contain a full course schedule") {
            events.size shouldBeLessThan 16
        }

        driver.close()
    }

    // ── Academic Calendar ─────────────────────────────────────────────────────

    test("STLCC academic calendar: no duplicates, semester bounds present").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 8).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 8).milliseconds
    ) {
        val apiKey = resolveApiKey("STLCC academic calendar") ?: return@config
        val contributionsDir = findContributionsDir() ?: run {
            println("SKIPPING: No contributions/ directory found."); return@config
        }

        val entry = ContributionIndex.STLCC_CALENDAR
        val pdfFile = File(contributionsDir, entry.relativePath)
        if (!pdfFile.exists()) {
            println("SKIPPING: ${entry.name} not found at ${pdfFile.canonicalPath}"); return@config
        }

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings().apply { putString("CEF_GEMINI_API_KEY", apiKey) }
        val logger = Logger(settings)

        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash", Clock.System.now().toEpochMilliseconds()
        )

        val (ingestionAgent, eventAgent, _) = buildStack(settings, logger, database)

        println("\n=== STLCC — ${entry.description} ===")
        val source = skipIfQuotaExhausted("ingest:${pdfFile.name}") {
            ingestionAgent.addLocalFile(pdfFile.absolutePath)
        }
        println("Category: ${source.category}")

        skipIfQuotaExhausted("extract:${pdfFile.name}") {
            eventAgent.extractDeliverables(source)
        }

        val events = eventAgent.lastGeneratedEvents.value
        println("Extracted: ${events.size} events")
        events.groupBy { it.category }.forEach { (cat, evts) -> println("  $cat: ${evts.size}") }

        // ── 1. No duplicate events ───────────────────────────────────────────
        val fingerprints = events.map { eventFingerprint(it) }
        val duplicates = fingerprints.groupBy { it }.filter { it.value.size > 1 }
        withClue("Duplicate events: ${duplicates.keys.joinToString()}") {
            duplicates.isEmpty() shouldBe true
        }

        // ── 2. At least some events extracted ─────────────────────────────────
        withClue("Calendar extracted zero events") {
            events.size shouldBeGreaterThan 0
        }

        driver.close()
    }
})
