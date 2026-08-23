package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import kotlin.time.Duration.Companion.milliseconds

/**
 * Debug repro: does splitting syllabus.txt into 3000-char fragments (like a real PDF import
 * does via SourceProcessor.split) break extraction that works fine as a single fragment?
 */
class SevenPartsReproIntegrationTest : FunSpec({

    test("syllabus.txt split into fragments (repro of real PDF chunking)").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("SEVEN PARTS REPRO") ?: return@config

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)
        val eventAgent = EventAgent(aiService, io.mockk.mockk<CalendarAgent>(relaxed = true), database, logger = logger)

        val stream = object {}.javaClass.classLoader.getResourceAsStream("syllabus.txt")
            ?: throw AssertionError("fixture not found")
        val text = stream.bufferedReader().use { it.readText() }

        val fragments = SourceProcessor.split(text)
        println("=== SPLIT INTO ${fragments.size} FRAGMENTS ===")
        fragments.forEachIndexed { i, f -> println("  fragment $i: ${f.text.length} chars, page=${f.pageNumber}") }

        val source = SourceItem("Syllabus (7-part repro)", fragments)
        eventAgent.extractDeliverables(source)

        val events = eventAgent.lastGeneratedEvents.value
        val warning = eventAgent.extractionWarning.value
        val status = eventAgent.statusMessage.value
        val errorState = eventAgent.errorState.value

        println("=== RESULT ===")
        println("events.size = ${events.size}")
        println("statusMessage = $status")
        println("extractionWarning = $warning")
        println("errorState = $errorState")
        events.forEach { println("  [${it.category}] ${it.title}") }
    }
})
