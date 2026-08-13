package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.FragmentEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class TaskDecompositionServiceTest : StringSpec({

    fun makeDeadline(id: String, title: String, date: LocalDate) = DayEvent(
        id = id,
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.DEADLINE,
        date = date
    )

    fun makeTasks() = listOf(
        DecomposedTask("Review notes", daysBeforeDue = 7, description = ""),
        DecomposedTask("Draft outline", daysBeforeDue = 5, description = ""),
    )

    "applyDecomposition called twice produces identical IDs — no duplicates" {
        val calendarAgent = mockk<CalendarAgent>(relaxed = true)
        val savedEvents = mutableListOf<Event>()

        coEvery { calendarAgent.saveEvent(any(), any()) } coAnswers {
            savedEvents.add(firstArg())
        }
        coEvery { calendarAgent.updateEvent(any(), any()) } returns Unit

        val service = TaskDecompositionService(
            aiService = mockk(relaxed = true),
            repository = calendarAgent
        )

        val deadline = makeDeadline("deadline-1", "Final Paper", LocalDate(2026, 12, 1))
        val tasks = makeTasks()

        service.applyDecomposition(deadline, tasks, "default")
        service.applyDecomposition(deadline, tasks, "default")

        // 4 save calls but only 2 distinct IDs (same tasks, same dates, same parent)
        savedEvents shouldHaveSize 4
        val ids = savedEvents.map { it.id }
        ids.toSet() shouldHaveSize 2
    }

    "applyDecomposition generates deterministic IDs based on parent id, title, and date" {
        val calendarAgent = mockk<CalendarAgent>(relaxed = true)
        val round1 = mutableListOf<Event>()
        val round2 = mutableListOf<Event>()
        var round = 1

        coEvery { calendarAgent.saveEvent(any(), any()) } coAnswers {
            val event = firstArg<Event>()
            if (round == 1) round1.add(event) else round2.add(event)
        }
        coEvery { calendarAgent.updateEvent(any(), any()) } returns Unit

        val service = TaskDecompositionService(
            aiService = mockk(relaxed = true),
            repository = calendarAgent
        )

        val deadline = makeDeadline("deadline-abc", "Midterm Essay", LocalDate(2026, 10, 15))
        val tasks = makeTasks()

        service.applyDecomposition(deadline, tasks, "default")
        round = 2
        service.applyDecomposition(deadline, tasks, "default")

        round1.map { it.id } shouldBe round2.map { it.id }
    }

    "applyDecomposition sets studyPlanStart on the target event" {
        val calendarAgent = mockk<CalendarAgent>(relaxed = true)
        var updatedTarget: Event? = null

        coEvery { calendarAgent.updateEvent(any(), any()) } coAnswers {
            updatedTarget = firstArg()
        }
        coEvery { calendarAgent.saveEvent(any(), any()) } returns Unit

        val service = TaskDecompositionService(
            aiService = mockk(relaxed = true),
            repository = calendarAgent
        )

        val deadline = makeDeadline("d-1", "Term Project", LocalDate(2026, 11, 20))

        service.applyDecomposition(deadline, makeTasks(), "default")

        updatedTarget?.studyPlanStart shouldBe "2026-11-13" // 7 days before Nov 20
    }

    "decompose resolves the originating event's source fragments and forwards them as context" {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepository = mockk<SourceRepository>()
        coEvery { sourceRepository.getFragmentsForSource("source-1") } returns listOf(
            FragmentEntity(
                id = "frag-1",
                sourceId = "source-1",
                text = "Essay due Dec 1. Worth 20% of the final grade.",
                pageNumber = null,
                sectionTitle = null,
                type = "TEXT",
                metadata = null
            )
        )

        val service = TaskDecompositionService(
            aiService = aiService,
            repository = mockk(relaxed = true),
            sourceRepository = sourceRepository
        )
        val deadline = makeDeadline("d-1", "Final Paper", LocalDate(2026, 12, 1))
            .withSourceId("source-1")

        service.decompose(deadline)

        coVerify(exactly = 1) { sourceRepository.getFragmentsForSource("source-1") }
        coVerify(exactly = 1) {
            aiService.decomposeTask(
                "Final Paper",
                "2026-12-01",
                "Essay due Dec 1. Worth 20% of the final grade."
            )
        }
    }

    "decompose falls back to empty context when the event has no linked source" {
        val aiService = mockk<AIService>(relaxed = true)
        val sourceRepository = mockk<SourceRepository>(relaxed = true)

        val service = TaskDecompositionService(
            aiService = aiService,
            repository = mockk(relaxed = true),
            sourceRepository = sourceRepository
        )
        val deadline = makeDeadline("d-2", "Untraced Task", LocalDate(2026, 12, 1))

        service.decompose(deadline)

        coVerify(exactly = 1) { aiService.decomposeTask("Untraced Task", "2026-12-01", "") }
    }
})
