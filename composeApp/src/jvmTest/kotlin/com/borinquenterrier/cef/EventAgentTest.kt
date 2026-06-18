package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.DriverFactory
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

private fun fixedClock(date: LocalDate): Clock = object : Clock {
    // Use noon UTC so todayIn(anyTimezone) resolves to `date` regardless of the host offset.
    override fun now(): Instant =
        Instant.fromEpochMilliseconds(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3600_000L)
}

/**
 * Demonstrates that the application logic can now run "Headless"
 * without any Compose UI dependencies.
 */
class HeadlessLogicTest : FunSpec({

    test("Should be able to instantiate and run logic via DependencyContainer") {
        // 1. Setup Headless Dependencies
        val settings = MapSettings()
        val logger = Logger(settings)
        val driverFactory = DriverFactory() // Use real factory in test

        // 2. Initialize Container
        val container = DependencyContainer(
            settings = settings,
            logger = logger,
            driverFactory = driverFactory,
            modelBasePath = "/tmp/models",
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = mockk(relaxed = true)
        )

        // 3. Verify member access
        container.googleAccountFlow.state.value shouldBe GoogleConnectionState.Unlinked
        container.eventAgent.isLoading.value shouldBe false

        // 4. Run a simple headless operation
        val text = "Test Event on 2026-01-01"
        val parts = SourceProcessor.process(text)

        parts.size shouldBe 1
        parts[0].text shouldBe text

        println("Headless logic successfully verified via DependencyContainer.")
    }

    test("EventAgent should pass existing calendar events to AIService to prevent collisions") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val database = null // Not needed for this pure logic test
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            database,
            NormalizationService(),
            logger = logger
        )

        // 1. Mock existing events in the calendar (e.g., a scheduled class)
        val existingClass = TimeEvent(
            title = "PHYS 401 Lecture",
            source = EventSource.CLASS,
            date = LocalDate(2026, 10, 14),
            startTime = LocalTime(9, 30),
            endTime = LocalTime(11, 30),
            category = AcademicCategory.CLASS
        )
        coEvery { mockCalendarAgent.getEvents("default") } returns listOf(existingClass)

        // 2. Mock AI response
        val mockAiResponse = listOf(
            TimeEvent(
                title = "Study for Midterm",
                source = EventSource.AI_GENERATED,
                date = LocalDate(2026, 10, 14),
                startTime = LocalTime(14, 0), // AI scheduled it AFTER the class
                endTime = LocalTime(16, 0),
                category = AcademicCategory.STUDY_BLOCK
            )
        )
        val scheduleSlot = slot<String>()
        coEvery {
            mockAiService.generateStudyPlan(
                any(),
                capture(scheduleSlot)
            )
        } returns mockAiResponse

        // 3. Run generateStudyPlan
        val source = SourceItem(
            "Mock Syllabus",
            listOf(SourceFragment("Midterm Exam on Oct 15", type = SourceType.TEXT))
        )
        eventAgent.generateStudyPlan(source)

        // 4. Verify that the EventAgent correctly queried the calendar and passed the formatted string to the AI
        coVerify { mockCalendarAgent.getEvents("default") }
        coVerify { mockAiService.generateStudyPlan(any(), any()) }

        // The formatted string should contain the existing class details so the AI knows to avoid it
        val capturedSchedule = scheduleSlot.captured
        capturedSchedule.contains("PHYS 401 Lecture") shouldBe true
        capturedSchedule.contains("2026-10-14") shouldBe true
        capturedSchedule.contains("09:30") shouldBe true

        // Verify the resulting generated events are available in state
        eventAgent.lastGeneratedEvents.value.size shouldBe 1
        eventAgent.lastGeneratedEvents.value[0].title shouldBe "Study for Midterm"
    }

    test("EventAgent.decomposeTask should store decomposed tasks and target event") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger
        )

        val deadline = DayEvent(
            title = "Research Paper",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 12, 1)
        )

        val mockTasks = listOf(
            DecomposedTask("Choose a topic", 14, "Pick something narrow enough."),
            DecomposedTask("Gather 5 sources", 10, "Focus on peer-reviewed articles."),
            DecomposedTask("Write outline", 7, "One sentence per section.")
        )
        coEvery { mockAiService.decomposeTask("Research Paper", "2026-12-01") } returns mockTasks

        eventAgent.decomposeTask(deadline)

        eventAgent.decomposedTasks.value shouldHaveSize 3
        eventAgent.decomposedTasks.value[0].title shouldBe "Choose a topic"
        eventAgent.decomposedTasks.value[1].daysBeforeDue shouldBe 10
        eventAgent.decompositionTarget.value shouldBe deadline
    }

    test("EventAgent.acceptDecomposition should save STUDY_BLOCK events calculated from daysBeforeDue") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger
        )

        val deadline = DayEvent(
            title = "Final Essay",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 12, 10)
        )

        val mockTasks = listOf(
            DecomposedTask("Draft introduction", 5, "Just get something on paper."),
            DecomposedTask("Write body paragraphs", 3, "Use your outline as a guide.")
        )
        coEvery { mockAiService.decomposeTask(any(), any()) } returns mockTasks
        coEvery { mockCalendarAgent.saveEvent(any(), any()) } returns Unit
        coEvery { mockCalendarAgent.updateEvent(any(), any()) } returns Unit

        eventAgent.decomposeTask(deadline)
        eventAgent.acceptDecomposition()

        val savedEvents = mutableListOf<Event>()
        coVerify(exactly = 2) { mockCalendarAgent.saveEvent(capture(savedEvents), "default") }

        val firstSaved = savedEvents[0]
        firstSaved.category shouldBe AcademicCategory.STUDY_BLOCK
        // 5 days before Dec 10 = Dec 5
        firstSaved.date shouldBe LocalDate(2026, 12, 5)

        val secondSaved = savedEvents[1]
        // 3 days before Dec 10 = Dec 7
        secondSaved.date shouldBe LocalDate(2026, 12, 7)

        // After accepting, state should be cleared
        eventAgent.decomposedTasks.value shouldHaveSize 0
        eventAgent.decompositionTarget.value shouldBe null
    }

    test("EventAgent.extractDeliverables should run auditSyllabus on SYLLABUS category sources and inject warnings") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger
        )

        val mockAuditResponse = """
            {
              "hasAmbiguities": true,
              "findings": [
                {
                  "type": "EXTERNAL_LMS",
                  "description": "Weekly assignments are hosted on Blackboard",
                  "severity": "HIGH"
                }
              ]
            }
        """.trimIndent()

        val mockEvents = listOf(
            DayEvent(
                title = "Midterm Exam",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 11, 10),
                warning = "Original Warning"
            )
        )

        coEvery { mockAiService.generateChatResponse(any()) } returns mockAuditResponse
        coEvery { mockAiService.generateCalendarEvents(any()) } returns mockEvents

        val source = SourceItem(
            title = "CS 101 Syllabus",
            fragments = listOf(SourceFragment("Midterm Exam 11/10", type = SourceType.TEXT)),
            category = SourceCategory.SYLLABUS
        )

        eventAgent.extractDeliverables(source)

        eventAgent.lastGeneratedEvents.value shouldHaveSize 1
        val extracted = eventAgent.lastGeneratedEvents.value[0]
        extracted.title shouldBe "Midterm Exam"
        extracted.warning shouldBe "Original Warning; [EXTERNAL_LMS] Weekly assignments are hosted on Blackboard"

        coVerify(exactly = 1) { mockAiService.generateChatResponse(any()) }
        coVerify(exactly = 1) { mockAiService.generateCalendarEvents(any()) }
    }

    test("EventAgent check-in operations (load, complete, skip, reschedule) should perform correct updates and state flows") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger
        )

        val missedEvent = DayEvent(
            id = "event1",
            title = "Missed Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 1),
            completionStatus = CompletionStatus.INCOMPLETE
        )

        coEvery { mockCalendarAgent.getIncompleteEventsBefore(any(), "default") } returns listOf(
            missedEvent
        )
        coEvery { mockCalendarAgent.updateEvent(any(), any()) } returns Unit
        coEvery { mockCalendarAgent.synchronize(any()) } returns Unit
        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()

        eventAgent.loadIncompleteEvents()
        eventAgent.incompleteEvents.value shouldHaveSize 1
        eventAgent.incompleteEvents.value[0].title shouldBe "Missed Study Block"

        eventAgent.markEventCompleted(missedEvent)
        coVerify(exactly = 1) {
            mockCalendarAgent.updateEvent(match {
                it.id == "event1" && it.completionStatus == CompletionStatus.COMPLETED
            }, "default")
        }

        eventAgent.skipEvent(missedEvent)
        coVerify(exactly = 1) {
            mockCalendarAgent.updateEvent(match {
                it.id == "event1" && it.completionStatus == CompletionStatus.SKIPPED
            }, "default")
        }

        eventAgent.rescheduleEvent(missedEvent)
        coVerify(exactly = 1) {
            mockCalendarAgent.updateEvent(match {
                it.id == "event1" && it.completionStatus == CompletionStatus.INCOMPLETE && it.date != LocalDate(
                    2026,
                    6,
                    1
                )
            }, "default")
        }
    }

    test("EventAgent error state, status and general clear operations") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger
        )

        // Test clearError and errorState
        val stateProp = eventAgent::class.java.getDeclaredField("_errorState")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<AgentError?>).value =
            AgentError.QuotaExhausted

        eventAgent.errorState.value shouldBe AgentError.QuotaExhausted
        eventAgent.clearError()
        eventAgent.errorState.value shouldBe null

        // Test updateStatus
        eventAgent.updateStatus("New Status Message")
        eventAgent.statusMessage.value shouldBe "New Status Message"

        // Test clear
        val lastGeneratedProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        lastGeneratedProp.isAccessible = true
        val event = DayEvent(
            title = "Temp Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2026, 1, 1)
        )
        (lastGeneratedProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(event)

        eventAgent.clear()
        eventAgent.lastGeneratedEvents.value shouldBe emptyList()
        eventAgent.statusMessage.value shouldBe "Select a source and an action."

        // Test clearDecomposition
        val decompTasksProp = eventAgent::class.java.getDeclaredField("_decomposedTasks")
        decompTasksProp.isAccessible = true
        (decompTasksProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<DecomposedTask>>).value =
            listOf(DecomposedTask("Task", 1, "Desc"))

        val decompTargetProp = eventAgent::class.java.getDeclaredField("_decompositionTarget")
        decompTargetProp.isAccessible = true
        (decompTargetProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<Event?>).value =
            event

        eventAgent.clearDecomposition()
        eventAgent.decomposedTasks.value shouldBe emptyList()
        eventAgent.decompositionTarget.value shouldBe null
    }

    test("EventAgent pushToCalendar with empty generated events should return empty list") {
        val mockCalendarAgent = mockk<CalendarAgent>()
        val eventAgent = EventAgent(
            mockk(),
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = Logger(MapSettings())
        )

        val result = eventAgent.pushToCalendar()
        result shouldBe emptyList()
    }

    test("EventAgent pushToCalendar should set status message and error state on repository exception") {
        val mockCalendarAgent = mockk<CalendarAgent>()
        val eventAgent = EventAgent(
            mockk(),
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = Logger(MapSettings()),
            clock = fixedClock(LocalDate(2026, 6, 17))
        )

        val futureEvent = DayEvent(
            title = "Temp Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2027, 1, 1)
        )
        val lastGeneratedProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        lastGeneratedProp.isAccessible = true
        (lastGeneratedProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(futureEvent)

        coEvery { mockCalendarAgent.getEvents(any()) } throws Exception("Database failure")

        val result = eventAgent.pushToCalendar()
        result shouldBe emptyList()
        eventAgent.statusMessage.value shouldBe "Sync Error: Database failure"
    }

    test("EventAgent pushToCalendar filters out past events and only pushes future ones") {
        val mockCalendarAgent = mockk<CalendarAgent>()
        val eventAgent = EventAgent(
            mockk(),
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = Logger(MapSettings()),
            clock = fixedClock(LocalDate(2026, 6, 17))
        )

        val pastEvent = DayEvent(
            title = "Past Deadline",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 1, 1)
        )
        val futureEvent = DayEvent(
            title = "Future Exam",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2027, 1, 1)
        )

        val lastGeneratedProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        lastGeneratedProp.isAccessible = true
        (lastGeneratedProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(pastEvent, futureEvent)

        coEvery { mockCalendarAgent.getEvents(any()) } returns emptyList()
        coEvery { mockCalendarAgent.saveEvent(any(), any()) } returns Unit

        eventAgent.pushToCalendar()

        coVerify(exactly = 0) { mockCalendarAgent.saveEvent(match { it.title == "Past Deadline" }, any()) }
        coVerify(exactly = 1) { mockCalendarAgent.saveEvent(match { it.title == "Future Exam" }, any()) }
    }

    test("EventAgent pushToCalendar with only past events returns empty list and sets skip status") {
        val mockCalendarAgent = mockk<CalendarAgent>()
        val eventAgent = EventAgent(
            mockk(),
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = Logger(MapSettings()),
            clock = fixedClock(LocalDate(2026, 6, 17))
        )

        val pastEvent1 = DayEvent(
            title = "Old Midterm",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2025, 6, 1)
        )
        val pastEvent2 = DayEvent(
            title = "Old Final",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 1, 1)
        )

        val lastGeneratedProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        lastGeneratedProp.isAccessible = true
        (lastGeneratedProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(pastEvent1, pastEvent2)

        val result = eventAgent.pushToCalendar()

        result shouldBe emptyList()
        eventAgent.statusMessage.value shouldBe "No future events to sync (2 past events skipped)."
        coVerify(exactly = 0) { mockCalendarAgent.saveEvent(any(), any()) }
    }

    test("EventAgent pushToCalendar status message includes skipped count when mix of past and future") {
        val mockCalendarAgent = mockk<CalendarAgent>()
        val eventAgent = EventAgent(
            mockk(),
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = Logger(MapSettings()),
            clock = fixedClock(LocalDate(2026, 6, 17))
        )

        val pastEvent = DayEvent(
            title = "Past Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 1, 1)
        )
        val futureEvent = DayEvent(
            title = "Future Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2027, 6, 1)
        )

        val lastGeneratedProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        lastGeneratedProp.isAccessible = true
        (lastGeneratedProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(pastEvent, futureEvent)

        coEvery { mockCalendarAgent.getEvents(any()) } returns emptyList()
        coEvery { mockCalendarAgent.saveEvent(any(), any()) } returns Unit

        eventAgent.pushToCalendar()

        eventAgent.statusMessage.value.contains("1 past events skipped") shouldBe true
    }

    test("EventAgent operations should handle exceptions gracefully") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger
        )
        val event = DayEvent(
            id = "event1",
            title = "Temp Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2026, 1, 1)
        )

        // loadIncompleteEvents exception
        coEvery {
            mockCalendarAgent.getIncompleteEventsBefore(
                any(),
                any()
            )
        } throws Exception("Failed to load")
        eventAgent.loadIncompleteEvents() // Should not throw

        // markEventCompleted exception
        coEvery { mockCalendarAgent.updateEvent(any(), any()) } throws Exception("Failed to update")
        eventAgent.markEventCompleted(event)
        eventAgent.statusMessage.value shouldBe "Error: Failed to update"

        // skipEvent exception
        eventAgent.skipEvent(event)
        eventAgent.statusMessage.value shouldBe "Error: Failed to update"

        // rescheduleEvent exception
        coEvery { mockCalendarAgent.getEvents(any()) } throws Exception("Failed to get events")
        eventAgent.rescheduleEvent(event)
        eventAgent.statusMessage.value shouldBe "Error: Failed to get events"
    }

    test("EventAgent rescheduleEvent should handle conflict case") {
        val mockAiService = mockk<AIService>()
        val mockCalendarAgent = mockk<CalendarAgent>()
        val logger = Logger(MapSettings())
        val today = LocalDate(2026, 6, 17)

        val eventAgent = EventAgent(
            mockAiService,
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = logger,
            clock = fixedClock(today)
        )

        val event = TimeEvent(
            id = "event1",
            title = "Missed Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = today,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0),
            completionStatus = CompletionStatus.INCOMPLETE
        )

        val conflictingEvents = (-7..3).map { offset ->
            TimeEvent(
                id = "conflict-$offset",
                title = "Busy Day $offset",
                source = EventSource.MANUAL,
                category = AcademicCategory.REGULAR,
                date = today.plus(offset, kotlinx.datetime.DateTimeUnit.DAY),
                startTime = LocalTime(9, 0),
                endTime = LocalTime(21, 0)
            )
        }

        coEvery { mockCalendarAgent.getEvents("default") } returns conflictingEvents

        eventAgent.rescheduleEvent(event)
        eventAgent.statusMessage.value shouldBe "Cannot reschedule: conflict detected."
    }
})
