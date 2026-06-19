package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-pipeline decomposition test for STLCC ENG 101 material.
 *
 * Deliberately separate from [StlccIntegrationTest] because this test makes
 * significantly more AI calls (extraction + one decomposition call per
 * DEADLINE/FINALS event). Run it explicitly when verifying the study-plan
 * pipeline end-to-end; do not include in the default integration run.
 *
 * ## Running
 *   ./gradlew :composeApp:jvmTest --tests "com.borinquenterrier.cef.StlccDecompositionIntegrationTest"
 */
class StlccDecompositionIntegrationTest : FunSpec({

    // Fix date to before semester so all deadlines are always "future".
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

    fun buildStack(settings: MapSettings, logger: Logger, database: AppDatabase): Triple<IngestionAgent, EventAgent, CalendarAgent> {
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
        val eventAgent = EventAgent(aiService, calendarAgent, database, logger = logger, clock = semesterClock)
        return Triple(ingestionAgent, eventAgent, calendarAgent)
    }

    test("ENG 101 weekly schedule: extract → push → auto-decompose produces study steps, no past-due").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds
    ) {
        val apiKey = resolveApiKey("STLCC decomposition pipeline") ?: return@config
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
        database.appDatabaseQueries.insertModel(
            "preferred_gemini_model", "gemini-2.5-flash", Clock.System.now().toEpochMilliseconds()
        )

        val (ingestionAgent, eventAgent, _) = buildStack(settings, logger, database)

        // ── 1. Extract ────────────────────────────────────────────────────────
        println("\n=== STLCC decomposition pipeline — ${entry.description} ===")
        val source = skipIfQuotaExhausted("ingest") { ingestionAgent.addLocalFile(pdfFile.absolutePath) }
        skipIfQuotaExhausted("extract") { eventAgent.extractDeliverables(source) }
        val events = eventAgent.lastGeneratedEvents.value
        println("Extracted: ${events.size} events")

        // ── 2. Push ───────────────────────────────────────────────────────────
        // semesterClock = 2026-05-01 → all summer 2026 events are future → none filtered.
        println("Pushing ${events.size} events to calendar...")
        skipIfQuotaExhausted("push") { eventAgent.pushToCalendar() }
        println("  push status: ${eventAgent.statusMessage.value}")

        withClue("Expected all ${events.size} events to push successfully") {
            eventAgent.statusMessage.value shouldContain "Success"
        }

        // ── 3. Auto-decompose ─────────────────────────────────────────────────
        val deadlineCount = events.count {
            it.category == AcademicCategory.DEADLINE || it.category == AcademicCategory.FINALS
        }
        println("Auto-decomposing $deadlineCount DEADLINE/FINALS events...")
        skipIfQuotaExhausted("decompose") { eventAgent.autoDecomposeDeliverables() }
        val decompStatus = eventAgent.statusMessage.value
        println("  decomp status: $decompStatus")

        withClue(
            "Expected study steps for $deadlineCount deadline(s) but got: \"$decompStatus\"\n" +
            "Events by category: ${events.groupBy { it.category }.mapValues { it.value.size }}"
        ) {
            decompStatus shouldContain "study steps added"
        }

        // semesterClock pins today to 2026-05-01, so all deadlines are future.
        withClue("semesterClock should make all deadlines future — 'past due' means clock injection is broken") {
            decompStatus shouldNotContain "past due"
        }

        // Sanity: expect at least 1 step per deadline.
        val stepCount = decompStatus.substringBefore(" study steps").toIntOrNull() ?: 0
        withClue("Expected at least $deadlineCount steps (1 per deadline), got $stepCount") {
            stepCount shouldBeGreaterThan deadlineCount - 1
        }

        println("✓ $stepCount study steps created for $deadlineCount deliverables. Clock pinning verified.")
        driver.close()
    }
})
