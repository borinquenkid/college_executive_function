package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class EventSyncTest : FunSpec({

    test("EventAgent.pushToCalendar should handle partial successes and isolate conflicts") {
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

        val safeEvent = DayEvent(
            title = "Safe Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2026, 10, 1)
        )

        val conflictingEvent = DayEvent(
            title = "Conflicting Event",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2026, 10, 2)
        )

        // Setup internal state with the events using the public clear() method to init
        eventAgent.clear()

        // Use reflection to set the private state for testing the push logic
        val stateProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(safeEvent, conflictingEvent)

        // Mock the calendar agent: one success, one conflict
        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()
        coEvery { mockCalendarAgent.saveEvent(safeEvent, "default") } returns Unit
        coEvery {
            mockCalendarAgent.saveEvent(
                conflictingEvent,
                "default"
            )
        } throws OverlapException(safeEvent, conflictingEvent)

        // ACT
        val conflicts = runBlocking { eventAgent.pushToCalendar() }

        // ASSERT
        conflicts shouldHaveSize 1
        conflicts[0].title shouldBe "Conflicting Event"

        // Verify state is updated to show ONLY the conflicts
        eventAgent.lastGeneratedEvents.value shouldHaveSize 1
        eventAgent.lastGeneratedEvents.value[0].title shouldBe "Conflicting Event"

        // Verify status message updated
        eventAgent.statusMessage.value shouldBe "Synced 1 events. 0 require professor contact, 1 other conflicts."
    }

    test("EventAgent.pushToCalendar should clear state on full success") {
        val mockCalendarAgent = mockk<CalendarAgent>()
        val eventAgent = EventAgent(
            mockk(),
            mockCalendarAgent,
            null,
            NormalizationService(),
            logger = Logger(MapSettings())
        )

        val event = DayEvent(
            title = "Test",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2027, 1, 1)
        )

        // Inject state
        val stateProp = eventAgent::class.java.getDeclaredField("_lastGeneratedEvents")
        stateProp.isAccessible = true
        (stateProp.get(eventAgent) as kotlinx.coroutines.flow.MutableStateFlow<List<Event>>).value =
            listOf(event)

        coEvery { mockCalendarAgent.getEvents(any()) } returns emptyList()
        coEvery { mockCalendarAgent.saveEvent(any(), any()) } returns Unit

        runBlocking { eventAgent.pushToCalendar() }

        eventAgent.lastGeneratedEvents.value shouldHaveSize 0
        eventAgent.statusMessage.value shouldBe "Success! All 1 events pushed to Google Calendar."
    }
})
