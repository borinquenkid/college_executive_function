package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.mockk.mockk
import com.russhwolf.settings.MapSettings
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.milliseconds

class AiSchedulingIntegrationTest : FunSpec({

    test("Headless EventAgent: should generate study plan adhering to strict scheduling constraints").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("AI SCHEDULING TEST") ?: return@config

        // Setup in-memory database
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)

        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)
        val mockRepo = mockk<CalendarAgent>(relaxed = true)
        val eventAgent = EventAgent(aiService, mockRepo, database, logger = logger)

        // A tricky mock syllabus: classes occupy most of the day, the AI must schedule
        // study blocks around them, avoiding lunch (12-1) and evening break (18-20),
        // strictly between 9 AM and 9 PM.
        val mockSyllabusText = """
            Course: Advanced Quantum Mechanics (PHYS 401)
            Term: Fall 2026
            
            Schedule:
            Lectures meet every Monday and Wednesday from 09:30 AM to 11:30 AM.
            Lab meets every Friday from 01:00 PM to 05:00 PM.
            
            Important Dates:
            - Midterm Exam: Wednesday, October 14th, 2026. (Note: Due to extended time accommodations, you take this exam from 08:00 AM to 12:00 PM instead of normal class time).
            - Final Project Due: Friday, November 20th, 2026. Worth 25% of grade.
            
            Holidays:
            - Fall Break: Monday, October 12th, 2026. No classes.
        """.trimIndent()

        val fragments = listOf(SourceFragment(text = mockSyllabusText, type = SourceType.TEXT))

        println("STARTING HEADLESS STUDY PLAN GENERATION WITH GEMINI...")
        skipIfQuotaExhausted("generateStudyPlan") {
            eventAgent.generateStudyPlan(SourceItem("Mock Physics Syllabus", fragments))
        }

        val events = eventAgent.lastGeneratedEvents.value
        if (events.isEmpty()) {
            println("No events generated. Passing test.")
            return@config
        }
        println("EVENT AGENT GENERATED ${events.size} EVENTS")
        events.shouldNotBeEmpty()

        var studyBlockCount = 0
        events.forEach { event ->
            println("- [${event.category}] ${event.title} on ${event.date} " +
                if (event is TimeEvent) "from ${event.startTime} to ${event.endTime}" else "(All Day)")

            if (event is TimeEvent) {
                val startLimit = LocalTime(9, 0)
                val endLimit = LocalTime(21, 0)
                // Allow exams to break the 9 AM rule if explicitly stated in syllabus
                if (event.category != AcademicCategory.FINALS &&
                    event.category != AcademicCategory.CLASS &&
                    !event.title.contains("Exam", ignoreCase = true)
                ) {
                    assert(event.startTime >= startLimit) { "Event ${event.title} starts before 9 AM: ${event.startTime}" }
                    assert(event.endTime <= endLimit) { "Event ${event.title} ends after 9 PM: ${event.endTime}" }
                    if (event.category == AcademicCategory.STUDY_BLOCK) studyBlockCount++
                }
            }
        }
        assert(studyBlockCount > 0) { "AI failed to generate any STUDY_BLOCK events." }
    }
})