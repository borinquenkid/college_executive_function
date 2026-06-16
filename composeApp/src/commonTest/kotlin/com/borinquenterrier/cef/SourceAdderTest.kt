package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SourceAdderTest : StringSpec({

    fun testScope() = CoroutineScope(Dispatchers.Unconfined)

    "addSource invokes onEventsAdded with AI results when configured" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        val events = listOf(mockk<Event>(relaxed = true))
        var eventsAdded: List<Event>? = null
        var errorReceived: AgentError? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { aiService.generateCalendarEvents(any()) } returns events
        coEvery { contextAgent.analyzeSource(source) } returns Unit

        val adder = SourceAdder(
            aiService, contextAgent, logger, testScope(),
            onEventsAdded = { eventsAdded = it },
            onError = { errorReceived = it }
        )

        adder.addSource(source)

        eventsAdded shouldBe events
        errorReceived shouldBe null
    }

    "addSource does not invoke onEventsAdded when AI not configured" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns false

        val adder = SourceAdder(
            aiService, contextAgent, logger, testScope(),
            onEventsAdded = { eventsAdded = it }
        )

        adder.addSource(mockk(relaxed = true))

        eventsAdded shouldBe null
    }

    "addSource calls onError with QuotaExhausted when Gemini quota is exceeded" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        var errorReceived: AgentError? = null
        var eventsAdded: List<Event>? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { aiService.generateCalendarEvents(any()) } throws Exception("QuotaExhausted: daily limit reached")

        val adder = SourceAdder(
            aiService, contextAgent, logger, testScope(),
            onEventsAdded = { eventsAdded = it },
            onError = { errorReceived = it }
        )

        adder.addSource(source)

        errorReceived shouldBe AgentError.QuotaExhausted
        eventsAdded shouldBe null
    }

    "addSource calls onError with GenericError on non-quota failure" {
        val aiService = mockk<AIService>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem>(relaxed = true)
        var errorReceived: AgentError? = null

        coEvery { aiService.isConfigured() } returns true
        coEvery { aiService.generateCalendarEvents(any()) } throws Exception("AI timeout")

        val adder = SourceAdder(
            aiService, contextAgent, logger, testScope(),
            onEventsAdded = {},
            onError = { errorReceived = it }
        )

        adder.addSource(source)

        errorReceived shouldBe AgentError.GenericError("AI timeout")
    }
})
