package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Run-to-run stability on a two-row-per-week schedule table (UT BIO 325L spring: "Monday 1/12
 * Lecture: …" above "1/13, 14, 15 Lab 1: …"). The same model at temperature 0 returned the lab as
 * its own 1/13 event on one run and merged into "Lecture … / Lab 1 …" @ 1/12 on the next
 * (2026-09-29). Invariants are taken from the syllabus rows and must hold on EVERY run.
 */
class ScheduleStabilityIntegrationTest : FunSpec({

    val runs = 3

    test("UT BIO 325L spring: lab rows keep their own dates on every run").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * runs * 4).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * runs * 4).milliseconds
    ) {
        val root = listOf(File("../contributions"), File("contributions")).firstOrNull { it.isDirectory }
            ?: run { println("SKIPPING: no contributions/"); return@config }
        val pdf = File(root, ContributionIndex.UT_BIO325L_GENETICS_LAB_SPRING.relativePath)
        val apiKey = resolveApiKey("SCHEDULE STABILITY INTEGRATION TEST") ?: return@config

        val labDates = mapOf(
            "Lab 1:" to (13..15).map { LocalDate(2026, 1, it) },
            "Lab 2:" to (20..22).map { LocalDate(2026, 1, it) },
        )
        val violations = mutableListOf<String>()

        repeat(runs) { run ->
            // Fresh DB + services per run: no analysis cache, no cross-run state.
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            AppDatabase.Schema.create(driver)
            val database = AppDatabase(driver)
            val settings = MapSettings().apply { putString("CEF_GEMINI_API_KEY", apiKey) }
            val logger = Logger(settings)
            val aiService: AIService = GroundingGuardAIService(
                CriticActorAIService(RealAIService(settings, logger, database), logger), logger
            )
            val calendarAgent = CalendarAgent(
                SqlDelightLocalCalendarRepository(database, settings),
                mockk<RemoteCalendarRepository>(relaxed = true), logger = logger
            )
            val ingestion = IngestionAgent(
                fileReader = LocalFileReader(), docxReader = mockk(relaxed = true), pdfReader = PdfReader(),
                webReader = mockk(relaxed = true), aiService = aiService,
                sourceRepository = SqlDelightSourceRepository(database)
            )
            val eventAgent = EventAgent(aiService, calendarAgent, database, logger = logger)

            val source = skipIfQuotaExhausted("ingest") { ingestion.addLocalFile(pdf.absolutePath) }
            skipIfQuotaExhausted("extract") { eventAgent.extractDeliverables(source) }
            val events = eventAgent.lastGeneratedEvents.value
            println("run ${run + 1}: ${events.size} events")

            events.filter { it.title.contains("Lecture") && Regex("""\bLab \d+:""").containsMatchIn(it.title) }
                .forEach { violations += "run ${run + 1}: merged lecture+lab '${it.title}' @ ${it.date}" }
            for ((label, allowed) in labDates) {
                events.filter { it.title.contains(label) && it.date !in allowed }
                    .forEach { violations += "run ${run + 1}: '${it.title}' @ ${it.date}, row says ${allowed.first()}–${allowed.last()}" }
            }
            events.filter { it.title.contains("Microscopy basics") && it.date != LocalDate(2026, 1, 12) }
                .forEach { violations += "run ${run + 1}: '${it.title}' @ ${it.date}, row says 2026-01-12" }
            driver.close()
        }

        violations.forEach { println("  VIOLATION $it") }
        violations.shouldBeEmpty()
    }
})
