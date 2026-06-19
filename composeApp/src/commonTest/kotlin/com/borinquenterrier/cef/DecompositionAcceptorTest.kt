package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class DecompositionAcceptorTest : FunSpec({

    val calendarId = "default"

    fun deadline(title: String) = DayEvent(
        title = title, source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE, date = LocalDate(2026, 9, 1)
    )

    fun task(title: String) = DecomposedTask(title = title, daysBeforeDue = 3, description = "desc")

    fun makeAcceptor(service: TaskDecompositionService = mockk(relaxed = true)) =
        DecompositionAcceptor(service)

    test("null target returns 0 without calling service") {
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val result = makeAcceptor(service).run(AcceptInput(listOf(task("Step")), null), calendarId)

        result shouldBe 0
        coVerify(exactly = 0) { service.applyDecomposition(any(), any(), any()) }
    }

    test("empty tasks list returns 0 without calling service") {
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val result = makeAcceptor(service).run(AcceptInput(emptyList(), deadline("Essay")), calendarId)

        result shouldBe 0
        coVerify(exactly = 0) { service.applyDecomposition(any(), any(), any()) }
    }

    test("valid tasks and target delegates to service and returns count") {
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val target = deadline("Essay")
        val tasks = listOf(task("Draft"), task("Review"), task("Submit"))
        coEvery { service.applyDecomposition(target, tasks, calendarId) } returns 3

        val result = makeAcceptor(service).run(AcceptInput(tasks, target), calendarId)

        result shouldBe 3
        coVerify(exactly = 1) { service.applyDecomposition(target, tasks, calendarId) }
    }

    test("service returns 0 (all blocked) — propagates correctly") {
        val service = mockk<TaskDecompositionService>(relaxed = true)
        val target = deadline("Essay")
        val tasks = listOf(task("Step"))
        coEvery { service.applyDecomposition(target, tasks, calendarId) } returns 0

        val result = makeAcceptor(service).run(AcceptInput(tasks, target), calendarId)

        result shouldBe 0
    }
})
