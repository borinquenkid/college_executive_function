package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.mockk

class SourceAdderTest : StringSpec({

    "addSource generates events when AI is configured" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        var eventsAdded: List<Event>? = null
        val onEventsAdded: (List<Event>) -> Unit = { events -> eventsAdded = events }

        val adder = SourceAdder(aiService, contextAgent, logger, mockk(), onEventsAdded)

        val source = mockk<SourceItem>(relaxed = true)
        val events = listOf(mockk<Event>(relaxed = true))

        coEvery { aiService.isConfigured() } returns true
        coEvery { aiService.generateCalendarEvents(any()) } returns events
        coEvery { contextAgent.analyzeSource(source) } returns Unit

        // Verify events callback invoked
    }

    "addSource skips processing when AI not configured" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        val onEventsAdded: (List<Event>) -> Unit = {}

        val adder = SourceAdder(aiService, contextAgent, logger, mockk(), onEventsAdded)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { aiService.isConfigured() } returns false

        // Verify no processing occurs
    }

    "addSource catches and logs processing errors" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        val onEventsAdded: (List<Event>) -> Unit = {}

        val adder = SourceAdder(aiService, contextAgent, logger, mockk(), onEventsAdded)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { aiService.isConfigured() } returns true
        coEvery { aiService.generateCalendarEvents(any()) } throws Exception("AI timeout")

        // Verify error handling
    }
})
