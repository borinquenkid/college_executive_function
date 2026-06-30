package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class CriticActorAIServiceTest : FunSpec({

    lateinit var delegate: AIService
    lateinit var criticActorService: CriticActorAIService
    lateinit var logger: Logger

    beforeEach {
        delegate = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        criticActorService = CriticActorAIService(delegate, logger)
    }

    test("passes through when delegate returns empty event list") {
        coEvery { delegate.generateCalendarEvents(any()) } returns emptyList()

        val result = criticActorService.generateCalendarEvents(emptyList())

        result shouldBe emptyList()
        coVerify(exactly = 1) { delegate.generateCalendarEvents(any()) }
        coVerify(exactly = 0) { delegate.generateChatResponse(any()) }
    }

    test("performs critique pass and returns refined events") {
        val firstPassEvents = listOf(
            DayEvent(
                title = "Valid Assignment",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 2)
            ),
            DayEvent(
                title = "Hallucinated Quiz",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 3)
            )
        )
        coEvery { delegate.generateCalendarEvents(any()) } returns firstPassEvents

        val critiqueResponseJson = """
            [
              {
                "title": "Valid Assignment",
                "type": "DAY",
                "category": "DEADLINE",
                "date": "2026-06-02"
              }
            ]
        """.trimIndent()
        coEvery { delegate.generateChatResponse(any()) } returns critiqueResponseJson

        val result = criticActorService.generateCalendarEvents(listOf(SourceFragment("dummy text")))

        result.size shouldBe 1
        result[0].title shouldBe "Valid Assignment"
        result[0].date shouldBe LocalDate(2026, 6, 2)

        coVerify(exactly = 1) { delegate.generateCalendarEvents(any()) }
        coVerify(exactly = 2) { delegate.generateChatResponse(any()) }
    }

    test("passes through when delegate returns empty study plan list") {
        coEvery { delegate.generateStudyPlan(any(), any()) } returns emptyList()

        val result = criticActorService.generateStudyPlan("syllabus text", "schedule")

        result shouldBe emptyList()
        coVerify(exactly = 1) { delegate.generateStudyPlan(any(), any()) }
        coVerify(exactly = 0) { delegate.generateChatResponse(any()) }
    }

    test("performs study plan critique pass and returns refined events") {
        val firstPassEvents = listOf(
            DayEvent(
                title = "Study Block 1",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                date = LocalDate(2026, 6, 2)
            ),
            DayEvent(
                title = "Study Block 2",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                date = LocalDate(2026, 6, 3)
            )
        )
        coEvery { delegate.generateStudyPlan(any(), any()) } returns firstPassEvents

        val critiqueResponseJson = """
            [
              {
                "title": "Study Block 1",
                "type": "DAY",
                "category": "STUDY_BLOCK",
                "date": "2026-06-02"
              }
            ]
        """.trimIndent()
        coEvery { delegate.generateChatResponse(any()) } returns critiqueResponseJson

        val result = criticActorService.generateStudyPlan("syllabus text", "schedule")

        result.size shouldBe 1
        result[0].title shouldBe "Study Block 1"
        result[0].date shouldBe LocalDate(2026, 6, 2)

        coVerify(exactly = 1) { delegate.generateStudyPlan(any(), any()) }
        coVerify(exactly = 2) { delegate.generateChatResponse(any()) }
    }

    test("revises chat response when critique finds outside knowledge or hallucination") {
        val originalPrompt = "What is the grade weighting for CS101?"
        val firstPassResponse = "Exams are 60%, Homework is 40%. The professor also likes coffee."
        val revisedResponse = "Exams are 60%, Homework is 40%."

        coEvery { delegate.generateChatResponse(originalPrompt) } returns firstPassResponse
        coEvery { delegate.generateChatResponse(match { it.contains("factual critique") }) } returns revisedResponse

        val result = criticActorService.generateChatResponse(originalPrompt)

        result shouldBe revisedResponse
        coVerify(exactly = 2) { delegate.generateChatResponse(any()) }
    }


    test("passes through when delegate returns empty task decomposition list") {
        coEvery { delegate.decomposeTask(any(), any()) } returns emptyList()

        val result = criticActorService.decomposeTask("task title", "2026-06-02")

        result shouldBe emptyList()
        coVerify(exactly = 1) { delegate.decomposeTask(any(), any()) }
        coVerify(exactly = 0) { delegate.generateChatResponse(any()) }
    }

    test("performs task decomposition critique pass and returns refined tasks") {
        val firstPassTasks = listOf(
            DecomposedTask(title = "Read intro", daysBeforeDue = 5, description = "Read chapter 1"),
            DecomposedTask(
                title = "Write code",
                daysBeforeDue = 2,
                description = "Write all the code in 10 hours"
            )
        )
        coEvery { delegate.decomposeTask(any(), any()) } returns firstPassTasks

        val critiqueResponseJson = """
            [
              {
                "title": "Read intro",
                "daysBeforeDue": 5,
                "description": "Read chapter 1"
              },
              {
                "title": "Write skeleton code",
                "daysBeforeDue": 3,
                "description": "Write basic functions (1-2 hours)"
              },
              {
                "title": "Implement detail logic",
                "daysBeforeDue": 1,
                "description": "Complete specific methods (1-2 hours)"
              }
            ]
        """.trimIndent()
        coEvery { delegate.generateChatResponse(any()) } returns critiqueResponseJson

        val result = criticActorService.decomposeTask("Write large project", "2026-06-02")

        result.size shouldBe 3
        result[0].title shouldBe "Read intro"
        result[1].title shouldBe "Write skeleton code"
        result[2].title shouldBe "Implement detail logic"

        coVerify(exactly = 1) { delegate.decomposeTask(any(), any()) }
        coVerify(exactly = 2) { delegate.generateChatResponse(any()) }
    }

    test("stops critique loop at maxIterations when output does not converge") {
        val firstPassEvents = listOf(
            DayEvent(
                title = "Task",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 2)
            )
        )
        coEvery { delegate.generateCalendarEvents(any()) } returns firstPassEvents

        var counter = 1
        coEvery { delegate.generateChatResponse(any()) } answers {
            """
                [
                  {
                    "title": "Task Variant ${counter++}",
                    "type": "DAY",
                    "category": "DEADLINE",
                    "date": "2026-06-02"
                  }
                ]
            """.trimIndent()
        }

        val result = criticActorService.generateCalendarEvents(listOf(SourceFragment("dummy text")))

        result.size shouldBe 1
        counter shouldBe 4 // 1 initial + 3 increments in loop
        coVerify(exactly = 3) { delegate.generateChatResponse(any()) }
    }

    test("detects and breaks on multi-turn oscillation cycles") {
        val firstPassEvents = listOf(
            DayEvent(
                title = "Task",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 2)
            )
        )
        coEvery { delegate.generateCalendarEvents(any()) } returns firstPassEvents

        val stateBJson = """
            [
              {
                "title": "Task State B",
                "type": "DAY",
                "category": "DEADLINE",
                "date": "2026-06-02"
              }
            ]
        """.trimIndent()

        val stateAJson = """
            [
              {
                "title": "Task",
                "type": "DAY",
                "category": "DEADLINE",
                "date": "2026-06-02"
              }
            ]
        """.trimIndent()

        var callCount = 0
        coEvery { delegate.generateChatResponse(any()) } answers {
            callCount++
            if (callCount == 1) stateBJson else stateAJson
        }

        val result = criticActorService.generateCalendarEvents(listOf(SourceFragment("dummy text")))

        result.size shouldBe 1
        coVerify(exactly = 2) { delegate.generateChatResponse(any()) }
    }

    test("passes through other methods directly without changes") {
        coEvery { delegate.isConfigured() } returns true
        coEvery { delegate.analyzeDocument(any()) } returns "Summary"
        coEvery { delegate.categorizeSource(any()) } returns SourceCategory.SYLLABUS

        criticActorService.isConfigured() shouldBe true
        criticActorService.analyzeDocument("text") shouldBe "Summary"
        criticActorService.categorizeSource("text") shouldBe SourceCategory.SYLLABUS

        coVerify(exactly = 1) { delegate.isConfigured() }
        coVerify(exactly = 1) { delegate.analyzeDocument(any()) }
        coVerify(exactly = 1) { delegate.categorizeSource(any()) }
    }
})
