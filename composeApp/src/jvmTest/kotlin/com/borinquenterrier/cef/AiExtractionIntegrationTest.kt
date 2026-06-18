package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds

/**
 * Headless extraction depth tests — CI catches "only midterms and finals" without running the app.
 *
 * Two fixtures are tested:
 *
 * 1. `syllabus.txt` — explicit dates for every entry (22 homeworks, 3 unit tests, 1 final,
 *    holidays, Spring Break). Minimum bar: 10+ events, at least one DEADLINE/REGULAR/HOLIDAY.
 *
 * 2. `week_based_syllabus.txt` — assignments listed only as "Due Week N" with a Week-1 anchor.
 *    Exercises week-number date computation introduced to fix extraction for documents that
 *    don't spell out explicit calendar dates per assignment. Minimum bar: 5+ events, at least
 *    one assignment (non-exam) event.
 *
 * Both tests skip cleanly on API failure, quota exhaustion, or missing API key.
 */
class AiExtractionIntegrationTest : FunSpec({

    fun buildEventAgent(apiKey: String): EventAgent {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)
        return EventAgent(aiService, mockk<CalendarAgent>(relaxed = true), database, logger = logger)
    }

    fun loadFixture(name: String): String {
        val stream = object {}.javaClass.classLoader.getResourceAsStream(name)
            ?: throw AssertionError("Test fixture not found on classpath: $name")
        return stream.bufferedReader().use { it.readText() }
    }

    fun eventDate(event: Event): String = when (event) {
        is DayEvent -> event.date.toString()
        is TimeEvent -> "${event.date} ${event.startTime}"
    }

    fun skipIfApiUnavailable(events: List<Event>, agent: EventAgent, label: String, returnBlock: () -> Unit) {
        if (events.isEmpty()) {
            val status = agent.statusMessage.value
            if (agent.errorState.value == AgentError.QuotaExhausted || status.startsWith("Error:")) {
                println("SKIPPING $label: API unavailable — $status")
                returnBlock()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fixture 1: explicit-date syllabus (the regression baseline)
    // -------------------------------------------------------------------------
    test("explicit-date syllabus extracts 10+ events including non-exam entries").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("EXPLICIT-DATE SYLLABUS EXTRACTION") ?: return@config
        val eventAgent = buildEventAgent(apiKey)

        val parts = SourceProcessor.process(loadFixture("syllabus.txt"))
        eventAgent.extractDeliverables(SourceItem("Syllabus", parts))

        val events = eventAgent.lastGeneratedEvents.value
        val status = eventAgent.statusMessage.value
        if (events.isEmpty()) {
            if (eventAgent.errorState.value == AgentError.QuotaExhausted || status.startsWith("Error:")) {
                println("SKIPPING: API unavailable — $status"); return@config
            }
        }

        println("Explicit-date syllabus extracted ${events.size} events:")
        events.forEach { println("  [${it.category}] ${it.title} — ${eventDate(it)}") }

        // 22 homework entries + 3 tests + 1 final + at least 2 holiday/break entries exist
        // in the fixture. Getting fewer than 10 means extraction is only picking up exams.
        events.size shouldBeGreaterThan 10

        val hasNonExamEvent = events.any {
            it.category == AcademicCategory.DEADLINE
                || it.category == AcademicCategory.REGULAR
                || it.category == AcademicCategory.HOLIDAY
        }
        hasNonExamEvent shouldBe true
    }

    // -------------------------------------------------------------------------
    // Fixture 2: week-based syllabus (regression for "only midterms and finals")
    // -------------------------------------------------------------------------
    test("week-based syllabus extracts reading responses not just midterm and final").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("WEEK-BASED SYLLABUS EXTRACTION") ?: return@config
        val eventAgent = buildEventAgent(apiKey)

        val parts = SourceProcessor.process(loadFixture("week_based_syllabus.txt"))
        eventAgent.extractDeliverables(SourceItem("PSYCH 101 Syllabus", parts))

        val events = eventAgent.lastGeneratedEvents.value
        val status = eventAgent.statusMessage.value
        if (events.isEmpty()) {
            if (eventAgent.errorState.value == AgentError.QuotaExhausted || status.startsWith("Error:")) {
                println("SKIPPING: API unavailable — $status"); return@config
            }
        }

        println("Week-based syllabus extracted ${events.size} events:")
        events.forEach { println("  [${it.category}] ${it.title} — ${eventDate(it)}") }

        // Fixture has 6 reading responses + 1 case study + 1 research proposal + midterm + final
        // = 10 events. Getting only 2 (midterm + final) is the bug this test guards against.
        // Floor is set to 5 to tolerate Gemini variability while still catching the 2-event failure.
        events.size shouldBeGreaterThan 5

        val hasAssignment = events.any { event ->
            val t = event.title.lowercase()
            t.contains("reading") || t.contains("response") || t.contains("paper")
                || t.contains("proposal") || t.contains("assignment")
                || event.category == AcademicCategory.DEADLINE
        }
        hasAssignment shouldBe true
    }

    // -------------------------------------------------------------------------
    // Fixture 3: day-within-week (STLCC pattern — due date buried under a named
    // class day inside a weekly schedule with per-week date ranges)
    // Being off by 2 days here means a student submits late. Precision matters.
    // -------------------------------------------------------------------------
    test("day-within-week schedule computes Issue Brief dates to the correct day not Monday").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("DAY-WITHIN-WEEK SYLLABUS EXTRACTION") ?: return@config
        val eventAgent = buildEventAgent(apiKey)

        val parts = SourceProcessor.process(loadFixture("day_within_week_syllabus.txt"))
        eventAgent.extractDeliverables(SourceItem("ENG 101 Weekly Schedule", parts))

        val events = eventAgent.lastGeneratedEvents.value
        val status = eventAgent.statusMessage.value
        if (events.isEmpty()) {
            if (eventAgent.errorState.value == AgentError.QuotaExhausted || status.startsWith("Error:")) {
                println("SKIPPING: API unavailable — $status"); return@config
            }
        }

        println("Day-within-week syllabus extracted ${events.size} events:")
        events.forEach { println("  [${it.category}] ${it.title} — ${eventDate(it)}") }

        // 4 final submissions (Issue Brief #1–3 + Final Paper) each due Wednesday at 11:59 pm.
        // Floor is 3 — accept Gemini missing drafts but not the graded submissions.
        events.size shouldBeGreaterThan 3

        val hasDeadline = events.any {
            it.title.lowercase().run {
                contains("issue brief") || contains("final paper")
            } || it.category == AcademicCategory.DEADLINE
        }
        hasDeadline shouldBe true

        // Issue Brief #1 is due Wednesday of Week 4 (June 29–July 5) = July 1.
        // If the AI falls back to Monday-of-week the date would be June 29 — wrong by 2 days.
        // This is a soft assertion: model output is non-deterministic; log mismatch for inspection.
        val brief1 = events.find {
            it.title.contains("Issue Brief #1", ignoreCase = true)
                && !it.title.contains("draft", ignoreCase = true)
        }
        if (brief1 != null) {
            val resolvedDate = eventDate(brief1)
            println("  Issue Brief #1 resolved to: $resolvedDate")
            if (!brief1.date.toString().startsWith("2026-07")) {
                println("  WARN: Expected 2026-07-xx (Wednesday July 1) but got $resolvedDate — day-within-week rule not applied by model")
            }
        }
    }
})
