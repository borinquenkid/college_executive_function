package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.runBlocking
import java.io.File

class AiExtractionIntegrationTest : FunSpec({

    test("Should extract events from syllabus.txt using real Gemini API") {
        // 1. Resolve API Key from .env
        val envFile = File("../.env")
        val envMap = if (envFile.exists()) {
            envFile.readLines()
                .filter { it.contains("=") && !it.startsWith("#") }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
        } else emptyMap()

        val apiKey = envMap["CEF_GEMINI_API_KEY"] ?: envMap["GEMINI_API_KEY"]
        val accessToken = envMap["GOOGLE_ACCESS_TOKEN"]
        
        if (apiKey == null && accessToken == null) {
            println("SKIPPING AI TEST: No credentials found in .env")
            return@test
        }

        // 2. Load syllabus text
        val syllabusStream = object {}.javaClass.classLoader.getResourceAsStream("syllabus.txt")
        if (syllabusStream == null) {
            throw AssertionError("Could not find syllabus.txt in test resources")
        }
        val syllabusText = syllabusStream.bufferedReader().use { it.readText() }

        // 3. Run Extraction
        val geminiService = if (accessToken != null) {
            GeminiAIService(accessToken = accessToken)
        } else {
            GeminiAIService(apiKey = apiKey)
        }
        val events = runBlocking { geminiService.generateCalendarEvents(syllabusText) }

        // 4. Verify
        println("EXTRACTED ${events.size} EVENTS FROM SYLLABUS")
        events.shouldNotBeEmpty()
        
        events.forEach { event ->
            println("- [${event.category}] ${event.title} on ${if (event is TimeEvent) event.date.toString() + " at " + event.startTime else (event as DayEvent).date}")
        }
    }
})
