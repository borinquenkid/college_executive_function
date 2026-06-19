package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class AutoDecomposerTest : FunSpec({

    val calendarId = "default"

    fun deadline(title: String, studyPlanStart: String? = null) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = LocalDate(2026, 9, 1),
        studyPlanStart = studyPlanStart
    )

    fun finals(title: String, studyPlanStart: String? = null) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.FINALS,
        date = LocalDate(2026, 12, 15),
        studyPlanStart = studyPlanStart
    )

    fun classEvent(title: String) = DayEvent(
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.CLASS,
        date = LocalDate(2026, 9, 1)
    )

    fun task(title: String) = DecomposedTask(title = title, daysBeforeDue = 3, description = "desc")

    fun makeDecomposer(
        repository: CalendarAgent = mockk(relaxed = true),
        service: TaskDecompositionService = mockk(relaxed = true)
    ) = AutoDecomposer(repository, service)

    // ── No unplanned events ────────────────────────────────────────────────────

    test("empty calendar returns zero counts and 'already planned' message") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns emptyList()

        val result = makeDecomposer(repo).run(Unit, calendarId)

        result.stepCount shouldBe 0
        result.deliverableCount shouldBe 0
        result.statusMessage shouldContain "already have study plans"
    }

    test("events with studyPlanStart already set are skipped") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns listOf(
            deadline("Essay", studyPlanStart = "2026-08-25")
        )
        val service = mockk<TaskDecompositionService>(relaxed = true)

        makeDecomposer(repo, service).run(Unit, calendarId)

        coVerify(exactly = 0) { service.decompose(any()) }
    }

    test("CLASS category events are skipped") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        coEvery { repo.getEvents(calendarId) } returns listOf(classEvent("Lecture"))
        val service = mockk<TaskDecompositionService>(relaxed = true)

        makeDecomposer(repo, service).run(Unit, calendarId)

        coVerify(exactly = 0) { service.decompose(any()) }
    }

    // ── Successful decomposition ───────────────────────────────────────────────

    test("single DEADLINE produces steps and correct message") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val event = deadline("Essay")
        coEvery { repo.getEvents(calendarId) } returns listOf(event)
        coEvery { service.decompose(event) } returns listOf(task("Draft"), task("Review"))
        coEvery { service.applyDecomposition(event, any(), calendarId) } returns 2

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 2
        result.deliverableCount shouldBe 1
        result.statusMessage shouldContain "2 study steps"
        result.statusMessage shouldContain "1 deliverable"
    }

    test("FINALS event is treated like DEADLINE") {
        val repo = mockk<CalendarAgent>(relaxed = true)
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val event = finals("Final Exam")
        coEvery { repo.getEvents(calendarId) } returns listOf(event)
        coEvery { service.decompose(event) } returns listOf(task("Study ch1"), task("Study ch2"), task("Review"))
        coEvery { service.applyDecomposition(event, any(), calendarId) } returns 3

        val result = makeDecomposer(repo, service).run(Unit, calendarId)

        result.stepCount shouldBe 3
        result.deliverableCount shouldBe 1
    }

    test("multiple deliverables accumulate step counts") {
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
    }

    // ── No tasks returned ─────────────────────────────────────────────────────

    test("decompose returns empty tasks — event is skipped and applyDecomposition not called") {
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

    test("all steps blocked returns 0 stepCount with appropriate message") {
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
})
