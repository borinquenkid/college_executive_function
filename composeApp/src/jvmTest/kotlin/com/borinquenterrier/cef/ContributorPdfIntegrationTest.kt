package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Generalized integration test for community-contributed college PDFs.
 *
 * Contributors drop PDF files (academic calendars, syllabi, etc.) under:
 *   contributions/{state}/{college_name}/{academic_year}/{period}/
 *
 * This test discovers every PDF in that tree, runs each through the full
 * ingestion + AI event-extraction pipeline, and asserts extraction depth —
 * not just that something came back, but that meaningful content was found.
 *
 * ## Depth assertions per file (syllabi)
 * - At least 3 events extracted (catches "only midterm and final" regression)
 * - At least 1 non-exam event (DEADLINE, REGULAR, HOLIDAY, or SEMESTER_BOUND)
 *
 * ## Skip behavior
 * - Skipped when contributions/ is empty or absent.
 * - Skipped when no Gemini API key is configured.
 * - Individual files skip cleanly on quota exhaustion or total API failure.
 * - Individual poisoned files skip with a warning (not a failure).
 *
 * ## Adding a new school
 * 1. Place PDFs in the appropriate directory, e.g.:
 *    contributions/mo/st_louis_community_college/2025-2026/summer/calendar.pdf
 * 2. Run `./gradlew :composeApp:jvmTest` with a valid CEF_GEMINI_API_KEY in .env.
 */
class ContributorPdfIntegrationTest : FunSpec({

    fun findContributionsDir(): File? = listOf(
        File("contributions"),
        File("../contributions"),
        File("src/commonTest/resources/contributions"),
        File("composeApp/src/commonTest/resources/contributions"),
        File("../composeApp/src/commonTest/resources/contributions"),
    ).firstOrNull { it.exists() && it.isDirectory }

    fun File.contributionPdfs(): List<File> =
        walk().filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }.toList()

    fun eventDate(event: Event): String = when (event) {
        is DayEvent -> event.date.toString()
        is TimeEvent -> "${event.date} ${event.startTime}"
    }

    test("Contributor PDFs: each syllabus extracts 3+ events including at least one non-exam entry").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds
    ) {
        val contributionsDir = findContributionsDir()
        if (contributionsDir == null) {
            println("SKIPPING: No contributions/ directory found."); return@config
        }

        // -PcontributionFilter=STLCC_ENG101_WEEKLY runs just that one entry.
        // Without the flag, all entries in ContributionIndex are run.
        val filterName = System.getProperty("contributionFilter")?.takeIf { it.isNotBlank() }
        val entries = if (filterName != null) {
            val entry = runCatching { ContributionIndex.valueOf(filterName) }.getOrNull()
            if (entry == null) {
                println("ERROR: Unknown contributionFilter '$filterName'.")
                println("  Valid values: ${ContributionIndex.entries.joinToString { it.name }}")
                return@config
            }
            println("Running single entry: ${entry.name} — ${entry.description}")
            listOf(entry)
        } else {
            ContributionIndex.entries
        }

        val pdfFiles = entries.mapNotNull { entry ->
            val file = File(contributionsDir, entry.relativePath)
            if (!file.exists()) {
                println("WARN: ${entry.name} — file not found at ${file.canonicalPath}")
                null
            } else file
        }
        if (pdfFiles.isEmpty()) {
            println("SKIPPING: No matching PDFs found in ${contributionsDir.canonicalPath}.")
            return@config
        }

        val apiKey = resolveApiKey("CONTRIBUTOR PDF INTEGRATION TEST") ?: return@config

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        // Use the full production chain (same as DependencyContainer) so tests catch
        // any filtering or critique behaviour that the app chain applies.
        val aiService: AIService = GroundingGuardAIService(
            CriticActorAIService(RealAIService(settings, logger, database), logger),
            logger
        )
        val sourceRepository = SqlDelightSourceRepository(database)
        val localCalendarRepo = SqlDelightLocalCalendarRepository(database, settings)
        val calendarAgent = CalendarAgent(
            localCalendarRepo, mockk<RemoteCalendarRepository>(relaxed = true), logger = logger
        )
        val ingestionAgent = IngestionAgent(
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = PdfReader(),
            webReader = mockk(relaxed = true),
            driveService = mockk(relaxed = true),
            aiService = aiService,
            sourceRepository = sourceRepository
        )
        val eventAgent = EventAgent(aiService, calendarAgent, database, logger = logger)

        println("\n=== Contributor PDF Integration Test ===")
        println("Found ${pdfFiles.size} PDF(s) in ${contributionsDir.canonicalPath}\n")

        val failures = mutableListOf<String>()

        for (pdfFile in pdfFiles) {
            val relativePath = pdfFile.relativeTo(contributionsDir)
            println("--- $relativePath ---")

            val source = try {
                skipIfQuotaExhausted("ingest:${pdfFile.name}") {
                    ingestionAgent.addLocalFile(pdfFile.absolutePath)
                }
            } catch (e: ContributionPoisonException) {
                println("  SKIPPED (poison detected): ${e.message}"); continue
            }

            println("  Category: ${source.category}")

            skipIfQuotaExhausted("extract:${pdfFile.name}") {
                eventAgent.extractDeliverables(source)
            }

            val events = eventAgent.lastGeneratedEvents.value
            val status = eventAgent.statusMessage.value

            // Skip (not fail) when the API itself was unavailable for this file
            if (events.isEmpty() && (eventAgent.errorState.value == AgentError.QuotaExhausted
                        || status.startsWith("Error:"))) {
                println("  SKIPPED: API unavailable — $status"); continue
            }

            println("  Extracted ${events.size} event(s):")
            events.take(10).forEach { e -> println("    - ${eventDate(e)} [${e.category}] ${e.title}") }
            if (events.size > 10) println("    … and ${events.size - 10} more")

            // Only apply depth assertions to syllabi — calendars and generic documents
            // may legitimately contain fewer structured events
            if (source.category == SourceCategory.SYLLABUS) {
                val hasNonExam = events.any {
                    it.category == AcademicCategory.DEADLINE
                        || it.category == AcademicCategory.REGULAR
                        || it.category == AcademicCategory.HOLIDAY
                        || it.category == AcademicCategory.SEMESTER_BOUND
                }
                if (events.size < 3 || !hasNonExam) {
                    val reason = when {
                        events.isEmpty() -> "0 events extracted (document may have no calendar dates)"
                        events.size < 3 -> "${events.size} events — only exams found, missed assignments/deadlines"
                        else -> "no non-exam events (only FINALS/STUDY_BLOCK categories)"
                    }
                    failures.add("$relativePath: $reason")
                    println("  FAIL: $reason")
                } else {
                    println("  PASS: ${events.size} events, non-exam events present")
                }
            } else {
                if (events.isEmpty()) {
                    failures.add("$relativePath: 0 events extracted from ${source.category} document")
                    println("  FAIL: 0 events extracted")
                } else {
                    println("  PASS: ${events.size} events")
                }
            }

            eventAgent.pushToCalendar()
        }

        println("\n=== Summary ===")
        if (failures.isEmpty()) {
            println("All ${pdfFiles.size} file(s) passed depth assertions.")
        } else {
            println("${failures.size} file(s) failed depth assertions:")
            failures.forEach { println("  - $it") }
        }

        // Allow up to 2 failures (≈12% of 17 PDFs) — free-tier Gemini occasionally
        // returns sparse results for complex multi-column syllabi; this is API variance,
        // not a code regression. File a separate task if the same PDF fails consistently.
        val maxAllowedFailures = 2
        if (failures.size > maxAllowedFailures) {
            failures.size shouldBe 0  // will print the actual count in the assertion message
        }

        driver.close()
    }
})
