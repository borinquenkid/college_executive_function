package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class AutoDecomposerTest : FunSpec({

    val calendarId = "default"
    val today = LocalDate(2026, 9, 15)
    val fixedClock = object : Clock {
        override fun now(): Instant =
            Instant.fromEpochMilliseconds(
                today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L
            )
    }

    fun deadline(title: String, date: LocalDate = LocalDate(2026, 10, 1), studyPlanStart: String? = null) =
        DayEvent(title = title, source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE, date = date, studyPlanStart = studyPlanStart)

    fun finals(title: String, date: LocalDate = LocalDate(2026, 12, 15), studyPlanStart: String? = null) =
        DayEvent(title = title, source = EventSource.AI_GENERATED,
            category = AcademicCategory.FINALS, date = date, studyPlanStart = studyPlanStart)

    fun classEvent(title: String) = DayEvent(title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS, date = LocalDate(2026, 10, 1))

    fun task(title: String) = DecomposedTask(title = title, daysBeforeDue = 3, description = "desc")

    fun makeDecomposer(
        repository: CalendarAgent = mockk(relaxed = true),
        service: TaskDecompositionService = mockk(relaxed = true),
        clock: Clock = fixedClock
    ) = AutoDecomposer(repository, service, clock)

    // ── No unplanned events ───────────────────────────────────────────────────

    test("empty calendar returns zero counts and 'already planned' message") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns emptyList()

        val result = makeDecomposer(repo).run(Unit, calendarId)

        result.stepCount shouldBe 0
        result.deliverableCount shouldBe 0
        result.lateCount shouldBe 0
        result.statusMessage shouldContain "already have study plans"
    }

    test("events with studyPlanStart already set are skipped") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns listOf(
            deadline("Essay", studyPlanStart = "2026-08-25")
        )

        makeDecomposer(repo, service).run(Unit, calendarId)

        coVerify(exactly = 0) { service.decompose(any()) }
    }

    test("CLASS category events are skipped") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns listOf(classEvent("Lecture"))

        makeDecomposer(repo, service).run(Unit, calendarId)

        coVerify(exactly = 0) { service.decompose(any()) }
    }

    // ── Future deliverables ───────────────────────────────────────────────────

    test("single future DEADLINE produces steps and correct message") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val event = deadline("Essay")
        coEvery { repo.getEvents(calendarId) } returns listOf(event)
        coEvery { service.decompose(event) } returns listOf(task("Draft"), task("Review"))
        coEvery { service.applyDecomposition(event, any(), calendarId) } returns 2

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 2
        result.deliverableCount shouldBe 1
        result.lateCount shouldBe 0
        result.statusMessage shouldContain "2 study steps"
        result.statusMessage shouldContain "1 deliverable"
        result.statusMessage shouldNotContain "past due"
    }

    test("FINALS event is treated like DEADLINE") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val event = finals("Final Exam")
        coEvery { repo.getEvents(calendarId) } returns listOf(event)
        coEvery { service.decompose(event) } returns listOf(task("Ch1"), task("Ch2"), task("Review"))
        coEvery { service.applyDecomposition(event, any(), calendarId) } returns 3

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 3
        result.deliverableCount shouldBe 1
        result.lateCount shouldBe 0
    }

    test("multiple future deliverables accumulate step counts") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val e1 = deadline("Essay")
        val e2 = finals("Final")
        coEvery { repo.getEvents(calendarId) } returns listOf(e1, e2)
        coEvery { service.decompose(e1) } returns listOf(task("Draft"))
        coEvery { service.decompose(e2) } returns listOf(task("Study"), task("Review"))
        coEvery { service.applyDecomposition(e1, any(), calendarId) } returns 1
        coEvery { service.applyDecomposition(e2, any(), calendarId) } returns 2

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 3
        result.deliverableCount shouldBe 2
        result.lateCount shouldBe 0
    }

    // ── Past-due detection ────────────────────────────────────────────────────

    test("past-due deadline is not decomposed and reported in lateCount") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val pastEvent = deadline("Missed Essay", date = LocalDate(2026, 9, 1))
        coEvery { repo.getEvents(calendarId) } returns listOf(pastEvent)

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        coVerify(exactly = 0) { service.decompose(any()) }
        result.lateCount shouldBe 1
        result.stepCount shouldBe 0
        result.statusMessage shouldContain "past due"
        result.statusMessage shouldContain "professor"
    }

    test("past-due FINALS is not decomposed and reported in lateCount") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val pastFinal = finals("Past Final", date = LocalDate(2026, 8, 1))
        coEvery { repo.getEvents(calendarId) } returns listOf(pastFinal)

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.lateCount shouldBe 1
        coVerify(exactly = 0) { service.decompose(any()) }
    }

    test("mix of future and past-due events: only future are decomposed") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val future = deadline("Future Essay", date = LocalDate(2026, 10, 1))
        val past = deadline("Past Essay", date = LocalDate(2026, 9, 1))
        coEvery { repo.getEvents(calendarId) } returns listOf(future, past)
        coEvery { service.decompose(future) } returns listOf(task("Draft"))
        coEvery { service.applyDecomposition(future, any(), calendarId) } returns 1

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 1
        result.deliverableCount shouldBe 1
        result.lateCount shouldBe 1
        result.statusMessage shouldContain "1 study steps"
        result.statusMessage shouldContain "past due"
        coVerify(exactly = 0) { service.decompose(past) }
    }

    test("all unplanned are past-due: no steps, lateCount correct, 'No upcoming' message") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns listOf(
            deadline("Past A", date = LocalDate(2026, 9, 1)),
            deadline("Past B", date = LocalDate(2026, 9, 5))
        )

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 0
        result.deliverableCount shouldBe 0
        result.lateCount shouldBe 2
        result.statusMessage shouldContain "No upcoming"
        result.statusMessage shouldContain "past due"
        coVerify(exactly = 0) { service.decompose(any()) }
    }

    // ── No tasks returned ─────────────────────────────────────────────────────

    test("decompose returns empty tasks — applyDecomposition not called") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val event = deadline("Essay")
        coEvery { repo.getEvents(calendarId) } returns listOf(event)
        coEvery { service.decompose(event) } returns emptyList()

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        coVerify(exactly = 0) { service.applyDecomposition(any(), any(), any()) }
        result.stepCount shouldBe 0
        result.statusMessage shouldContain "already have study plans or no steps"
    }

    test("all steps blocked returns 0 stepCount") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val event = deadline("Essay")
        coEvery { repo.getEvents(calendarId) } returns listOf(event)
        coEvery { service.decompose(event) } returns listOf(task("Step"))
        coEvery { service.applyDecomposition(event, any(), calendarId) } returns 0

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 0
        result.statusMessage shouldContain "already have study plans or no steps"
    }

    // ── Today's deadline is still future ─────────────────────────────────────

    test("deadline on today is treated as future and decomposed") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val todayEvent = deadline("Today Essay", date = today)
        coEvery { repo.getEvents(calendarId) } returns listOf(todayEvent)
        coEvery { service.decompose(todayEvent) } returns listOf(task("Submit"))
        coEvery { service.applyDecomposition(todayEvent, any(), calendarId) } returns 1

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.lateCount shouldBe 0
        result.stepCount shouldBe 1
    }
})
