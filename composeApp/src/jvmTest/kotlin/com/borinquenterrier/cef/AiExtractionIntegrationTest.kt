package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds

class AiExtractionIntegrationTest : FunSpec({

    test("Headless EventAgent: should extract deliverables from syllabus using Gemini").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("AI EXTRACTION TEST") ?: return@config

        // Setup in-memory database
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)

        // Setup dependencies
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)
        val mockRepo = mockk<CalendarAgent>(relaxed = true)
        val eventAgent = EventAgent(aiService, mockRepo, database, logger = logger)

        // Load syllabus text
        val syllabusStream = object {}.javaClass.classLoader.getResourceAsStream("syllabus.txt")
            ?: throw AssertionError("Could not find syllabus.txt in test resources")
        val syllabusText = syllabusStream.bufferedReader().use { it.readText() }
        val parts = SourceProcessor.process(syllabusText)

        println("STARTING HEADLESS EXTRACTION WITH GEMINI...")
        skipIfQuotaExhausted("extractDeliverables") {
            eventAgent.extractDeliverables(SourceItem("Syllabus", parts))
        }

        val events = eventAgent.lastGeneratedEvents.value
        if (events.isEmpty() && eventAgent.errorState.value == AgentError.QuotaExhausted) {
            println("SKIPPING test: Quota exhausted during extraction.")
            return@config
        }
        println("STUDIO FLOW GENERATED ${events.size} DELIVERABLES")
        events.forEach { println("- [${it.category}] ${it.title}") }

        events.shouldNotBeEmpty()
    }
})

