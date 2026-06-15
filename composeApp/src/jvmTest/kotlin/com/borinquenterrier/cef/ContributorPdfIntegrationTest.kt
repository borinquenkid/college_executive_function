package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.mockk.mockk
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Generalized integration test for community-contributed college PDFs.
 *
 * Contributors drop PDF files (academic calendars, syllabi, etc.) under:
 *   contributions/{state}/{college_name}/{academic_year}/{period}/
 *
 * This test discovers every PDF in that tree and runs it through the full
 * ingestion + AI event-extraction pipeline, asserting at least one event
 * is produced across all files.
 *
 * ## Adding a new school
 * 1. Place PDFs in the appropriate directory, e.g.:
 *    contributions/mo/st_louis_community_college/2025-2026/summer/calendar.pdf
 * 2. Run `./gradlew :composeApp:jvmTest` with a valid CEF_GEMINI_API_KEY in .env.
 *
 * ## Skip behavior
 * - Skipped when contributions/ is empty or absent.
 * - Skipped when no Gemini API key is configured.
 * - Skipped cleanly on quota exhaustion.
 * - Individual poisoned files are skipped with a warning (not a failure).
 * - Fails hard after [AI_INTEGRATION_TIMEOUT_MS] × (file count) to prevent hangs.
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

    test("Contributor PDFs: should ingest all PDFs in contributions/ and extract events").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 15).milliseconds
    ) {
        val contributionsDir = findContributionsDir()
        if (contributionsDir == null) {
            println("SKIPPING: No contributions/ directory found.")
            return@test
        }

        val pdfFiles = contributionsDir.contributionPdfs()
        if (pdfFiles.isEmpty()) {
            println("SKIPPING: No PDFs in ${contributionsDir.canonicalPath}.")
            println("  Add PDFs under contributions/{state}/{college}/{year}/{period}/")
            return@test
        }

        val apiKey = resolveApiKey("CONTRIBUTOR PDF INTEGRATION TEST") ?: return@test

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)

        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)

        val sourceRepository = SqlDelightSourceRepository(database)
        val localCalendarRepo = SqlDelightLocalCalendarRepository(database, settings)
        val remoteCalendarRepo = mockk<RemoteCalendarRepository>(relaxed = true)
        val calendarAgent = CalendarAgent(localCalendarRepo, remoteCalendarRepo, logger = logger)

        val ingestionAgent = IngestionAgent(
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = PdfReader(),
            webReader = mockk(relaxed = true),
            driveService = mockk(relaxed = true),
            aiService = aiService,
            sourceRepository = sourceRepository
        )

        val eventAgent = EventAgent(
            aiService = aiService,
            repository = calendarAgent,
            database = database,
            logger = logger
        )

        println("\n=== Contributor PDF Integration Test ===")
        println("Found ${pdfFiles.size} PDF(s) in ${contributionsDir.canonicalPath}")

        for (pdfFile in pdfFiles) {
            val relativePath = pdfFile.relativeTo(contributionsDir)
            println("\n--- $relativePath ---")

            val source = try {
                skipIfQuotaExhausted("ingest:${pdfFile.name}") {
                    ingestionAgent.addLocalFile(pdfFile.absolutePath)
                }
            } catch (e: ContributionPoisonException) {
                println("  SKIPPED (poison detected): ${e.message}")
                continue
            }

            println("  Category: ${source.category}")

            skipIfQuotaExhausted("extract:${pdfFile.name}") {
                eventAgent.extractDeliverables(source)
            }
            eventAgent.pushToCalendar()

            val events = eventAgent.lastGeneratedEvents.value
            println("  Extracted ${events.size} event(s):")
            events.take(10).forEach { e ->
                println("    - ${e.date} [${e.category}] ${e.title}")
            }
            if (events.size > 10) println("    … and ${events.size - 10} more")
        }

        val allCalendarEvents = calendarAgent.getEvents("default")
        println("\nTotal calendar events from all contributor PDFs: ${allCalendarEvents.size}")
        allCalendarEvents.shouldNotBeEmpty()

        driver.close()
    }
})
