package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.runBlocking
import java.io.File
import io.mockk.mockk
import com.russhwolf.settings.MapSettings
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase

class AiExtractionIntegrationTest : FunSpec({

    test("Headless StudioFlow: should extract deliverables from syllabus using Gemini") {
        // 1. Resolve Credentials
        val envFile = listOf(File("../.env"), File(".env")).find { it.exists() }
        val envMap = envFile?.readLines()?.associate { 
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            key to value 
        } ?: emptyMap()

        val apiKey = (envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"])?.takeIf { it.isNotBlank() }
        
        if (apiKey == null) {
            println("SKIPPING AI EXTRACTION TEST: No Gemini API Key found in .env")
            return@test
        }

        // 2. Setup in-memory database
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)

        // 3. Setup dependencies
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService = AIService(settings, logger, database, null)
        val mockRepo = mockk<UnifiedCalendarRepository>(relaxed = true)
        
        val studioFlow = StudioFlow(aiService, mockRepo, database, logger = logger)

        // 4. Load syllabus text
        val syllabusStream = object {}.javaClass.classLoader.getResourceAsStream("syllabus.txt")
        if (syllabusStream == null) {
            throw AssertionError("Could not find syllabus.txt in test resources")
        }
        val syllabusText = syllabusStream.bufferedReader().use { it.readText() }
        val parts = SourceProcessor.process(syllabusText)

        // 5. Run Flow Headless
        println("STARTING HEADLESS EXTRACTION WITH GEMINI...")
        runBlocking {
            studioFlow.extractDeliverables(SourceItem("Syllabus", parts))
        }

        // 6. Verify
        val events = studioFlow.lastGeneratedEvents.value
        println("STUDIO FLOW GENERATED ${events.size} DELIVERABLES")
        
        if (events.isEmpty()) {
            println("WARNING: No events generated. Check if models are exhausted in logs.")
        } else {
            events.forEach { event ->
                println("- [${event.category}] ${event.title}")
            }
        }
        
        events.shouldNotBeEmpty()
    }
})
