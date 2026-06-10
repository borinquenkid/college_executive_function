package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration test for the AI text-to-events pipeline.
 *
 * Scope: given a fixed, known text input (syllabus.txt), verify that the AI correctly
 * identifies and structures academic events with accurate dates and categories.
 *
 * Format extraction (DOCX/PDF/HTML → text) is tested separately in DocxReaderTest,
 * PdfReaderTest, and WebSourceReaderTest.
 *
 * ## Skip behaviour
 * - Skipped when no API key is present in `.env`.
 * - Skipped cleanly (not failed) when the daily Gemini quota is exhausted.
 * - Fails hard after [AI_INTEGRATION_TIMEOUT_MS] to prevent 70-minute hangs
 *   caused by repeated RPM-throttling retries.
 */
class MultiFormatAiIntegrationTest : FunSpec({

    test("AI should extract structured events from a real syllabus text").config(
        timeout = AI_INTEGRATION_TIMEOUT_MS.milliseconds
    ) {
        val apiKey = resolveApiKey("AI TEXT-TO-EVENTS TEST")
            ?: return@config

        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val aiService: AIService = RealAIService(settings, Logger(settings), null)

        // Synthetic fixture with fully-specified 2026 dates — no AI inference required
        val fixtureFile = listOf(
            File("src/commonTest/resources/syllabus.txt"),
            File("composeApp/src/commonTest/resources/syllabus.txt"),
            File("../composeApp/src/commonTest/resources/syllabus.txt")
        ).find { it.exists() } ?: throw IllegalStateException("Could not find syllabus.txt fixture")

        val fragments = SourceProcessor.process(fixtureFile.readText())

        // Run AI extraction — skips cleanly if daily quota is exhausted
        val events = skipIfQuotaExhausted("generateCalendarEvents") {
            aiService.generateCalendarEvents(fragments)
        }

        println("Extracted ${events.size} events from syllabus:")
        events.forEach { println("  - ${it.date} | ${it.category} | ${it.title}") }

        // Should extract a meaningful number of events from a full syllabus
        events.shouldNotBeEmpty()
        events.size shouldBeGreaterThan 5

        fun dateOf(e: Event) = when (e) {
            is TimeEvent -> e.date
            is DayEvent -> e.date
        }

        // Final exam must appear — explicitly listed in syllabus as "5/4 Final (Test Four)"
        val hasFinalExam = events.any { e ->
            val d = dateOf(e)
            d == LocalDate(2026, 5, 4) &&
                    (e.category == AcademicCategory.FINALS || e.category == AcademicCategory.DEADLINE)
        }
        hasFinalExam shouldNotBe false

        // A unit test must appear — "2/9 TEST Test One"
        val hasTestOne = events.any { e ->
            dateOf(e) == LocalDate(2026, 2, 9) &&
                    (e.category == AcademicCategory.FINALS || e.category == AcademicCategory.DEADLINE || e.category == AcademicCategory.REGULAR)
        }
        hasTestOne shouldNotBe false

        // MLK Jr. Day holiday must appear — explicitly listed as "1/19 No Class – MLK Jr. Day"
        val hasMLKDay = events.any { e ->
            dateOf(e) == LocalDate(2026, 1, 19) &&
                    (e.category == AcademicCategory.HOLIDAY || e.category == AcademicCategory.REGULAR)
        }
        hasMLKDay shouldNotBe false

        println("SUCCESS: AI correctly extracted exam dates and holidays from syllabus text.")
    }
})

