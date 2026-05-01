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

    test("Headless StudioFlow: should extract deliverables from syllabus using local Llamatik model") {
        // 1. Resolve Model Path
        val userDir = System.getProperty("user.dir") ?: "."
        val root = if (userDir.endsWith("composeApp")) {
            File(userDir).parentFile ?: File(userDir)
        } else {
            File(userDir)
        }
        val modelDir = File(root, "models")
        val modelFile = File(modelDir, "Qwen3.5-9B-Q4_K_M.gguf")
        
        if (!modelFile.exists()) {
            println("AI model missing. Downloading to ${modelFile.absolutePath}...")
            val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.java.Java)
            val modelManager = ModelManager(httpClient, modelDir.absolutePath)
            runBlocking {
                modelManager.downloadModel().collect { progress ->
                    if (progress.isDone) println("Download complete.")
                }
            }
            httpClient.close()
        }

        if (!modelFile.exists()) {
            throw AssertionError("Failed to download AI model.")
        }

        // 2. Setup in-memory database
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)

        // 3. Setup dependencies
        val settings = MapSettings()
        val logger = Logger(settings)
        // Pass the model path to AIService
        val aiService = AIService(settings, logger, database, modelFile.absolutePath)
        val mockRepo = mockk<UnifiedCalendarRepository>(relaxed = true)
        
        val studioFlow = StudioFlow(aiService, mockRepo, database, logger = logger)

        // 4. Load syllabus text
        val syllabusStream = object {}.javaClass.classLoader.getResourceAsStream("syllabus.txt")
        if (syllabusStream == null) {
            throw AssertionError("Could not find syllabus.txt in test resources")
        }
        val syllabusText = syllabusStream.bufferedReader().use { it.readText() }
        val chunks = TextChunker.chunk(syllabusText)

        // 5. Run Flow Headless
        println("STARTING HEADLESS EXTRACTION...")
        runBlocking {
            studioFlow.extractDeliverables(SourceItem("Syllabus", chunks))
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
