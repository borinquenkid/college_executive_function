package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/**
 * Covers the shared SemesterFilter object and CalendarAgent.getSemesterEvents — the calendar
 * view's semester window, which remote-synced STUDENT events (that bypass ingestion) rely on.
 */
class SemesterViewFilterTest : FunSpec({

    fun day(title: String, date: String, source: EventSource = EventSource.STUDENT) =
        DayEvent(title = title, source = source, date = LocalDate.parse(date))

    val summer = StudyPreferences(semesterStart = "2026-06-01", semesterEnd = "2026-08-01")

    context("SemesterFilter (pure)") {
        val events = listOf(
            day("Before term", "2026-05-31"),
            day("First day", "2026-06-01"),
            day("Midterm", "2026-07-15"),
            day("Last day", "2026-08-01"),
            day("After term", "2026-08-02")
        )

        test("keeps only events inside the inclusive window") {
            SemesterFilter.apply(events, summer).map { it.title } shouldContainExactlyInAnyOrder
                listOf("First day", "Midterm", "Last day")
        }

        test("no filtering when a bound is missing or unparseable") {
            SemesterFilter.apply(events, StudyPreferences(semesterStart = "2026-06-01")).size shouldBe events.size
            SemesterFilter.apply(events, StudyPreferences()).size shouldBe events.size
            SemesterFilter.apply(events, StudyPreferences(semesterStart = "x", semesterEnd = "2026-08-01")).size shouldBe events.size
        }

        test("window() requires both bounds to parse") {
            SemesterFilter.window(summer) shouldBe
                (LocalDate.parse("2026-06-01") to LocalDate.parse("2026-08-01"))
            SemesterFilter.window(StudyPreferences(semesterEnd = "2026-08-01")) shouldBe null
        }
    }

    context("CalendarAgent.getSemesterEvents") {

        fun agentReturning(events: List<Event>, prefs: StudyPreferences): CalendarAgent {
            val localRepo = mockk<StudentCalendarRepository>(relaxed = true)
            coEvery { localRepo.getAllEvents(any()) } returns events
            val prefsPort = mockk<PreferencesPort> { coEvery { getPreferences() } returns prefs }
            return CalendarAgent(
                localRepo = localRepo,
                remoteRepo = mockk(relaxed = true),
                preferencesRepository = prefsPort
            )
        }

        val mixed = listOf(
            day("Out-of-term remote event", "2026-09-07"),   // a STUDENT event synced from Google
            day("In-term deadline", "2026-07-15")
        )

        test("hides out-of-semester events from the view") {
            val agent = agentReturning(mixed, summer)
            runBlocking { agent.getSemesterEvents() }.map { it.title } shouldContainExactlyInAnyOrder
                listOf("In-term deadline")
        }

        test("getEvents (full set) is unaffected — reconcile/delete still see everything") {
            val agent = agentReturning(mixed, summer)
            runBlocking { agent.getEvents() }.size shouldBe 2
        }

        test("no semester configured → view shows everything") {
            val agent = agentReturning(mixed, StudyPreferences())
            runBlocking { agent.getSemesterEvents() }.size shouldBe 2
        }
    }
})
