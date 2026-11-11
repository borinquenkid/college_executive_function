package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/**
 * The semester window is enforced at the generation choke point so BOTH the studio-staging path
 * and the auto-push pipeline drop out-of-term events before they can reach the calendar.
 */
class EventGenerationServiceSemesterTest : FunSpec({

    fun deadline(title: String, date: String) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = LocalDate.parse(date)
    )

    fun service(prefs: StudyPreferences): EventGenerationService {
        val ai = mockk<AIService>(relaxed = true)
        coEvery { ai.generateCalendarEvents(any()) } returns listOf(
            deadline("In-term deadline", "2026-07-15"),
            deadline("Out-of-term deadline", "2026-09-07")
        )
        val prefsPort = mockk<PreferencesPort> { coEvery { getPreferences() } returns prefs }
        return EventGenerationService(
            aiService = ai,
            normalizationService = NormalizationService(),
            syllabusAuditor = mockk(relaxed = true),
            preferencesRepository = prefsPort,
            clock = FIXED_TEST_CLOCK
        )
    }

    val source = SourceItem(
        title = "syllabus",
        fragments = listOf(SourceFragment(text = "Homework due", type = SourceType.TEXT)),
        category = SourceCategory.OTHER
    )

    test("extractDeliverables drops events outside the configured semester") {
        val svc = service(StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01"))
        val result = runBlocking { svc.extractDeliverables(source) }
        result.map { it.title } shouldContainExactlyInAnyOrder listOf("In-term deadline")
    }

    test("no semester configured → all events pass through") {
        val svc = service(StudyPreferences())
        val result = runBlocking { svc.extractDeliverables(source) }
        result.map { it.title } shouldContainExactlyInAnyOrder
            listOf("In-term deadline", "Out-of-term deadline")
    }

    test("extractWithOutcome reports how many events the window dropped") {
        val svc = service(StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01"))
        val outcome = runBlocking { svc.extractWithOutcome(source) }
        outcome.events.map { it.title } shouldContainExactlyInAnyOrder listOf("In-term deadline")
        outcome.outsideWindow shouldBe 1
        outcome.window shouldBe (LocalDate(2026, 6, 1) to LocalDate(2026, 8, 1))
    }

    test("no semester configured → nothing dropped and no window reported") {
        val outcome = runBlocking { service(StudyPreferences()).extractWithOutcome(source) }
        outcome.outsideWindow shouldBe 0
        outcome.window shouldBe null
    }
})
