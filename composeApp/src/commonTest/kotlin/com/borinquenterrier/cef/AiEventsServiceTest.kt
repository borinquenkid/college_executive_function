package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class AiEventsServiceTest : FunSpec({
    test("should initialize with empty events list") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val service = AiEventsService()
        
        service.aiGeneratedEvents.value.shouldBeEmpty()
    }

    test("should add events to list") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val service = AiEventsService()
        
        val event = TimeEvent(
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 30)
        )
        
        service.addEvents(listOf(event))
        
        service.aiGeneratedEvents.value.shouldHaveSize(1)
        service.aiGeneratedEvents.value[0].title shouldBe "Study Math"
    }

    test("should accumulate multiple add events calls") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val service = AiEventsService()
        
        val event1 = TimeEvent(
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 30)
        )
        
        val event2 = TimeEvent(
            title = "Study Physics",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 11),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 30)
        )
        
        service.addEvents(listOf(event1))
        service.addEvents(listOf(event2))
        
        service.aiGeneratedEvents.value.shouldHaveSize(2)
    }

    test("should clear all events") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val service = AiEventsService()
        
        val event1 = TimeEvent(
            title = "Study Math",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 30)
        )
        
        service.addEvents(listOf(event1))
        service.aiGeneratedEvents.value.shouldHaveSize(1)
        
        service.clearEvents()
        
        service.aiGeneratedEvents.value.shouldBeEmpty()
    }

    test("should handle clearing empty events list") {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val service = AiEventsService()
        
        service.aiGeneratedEvents.value.shouldBeEmpty()
        service.clearEvents()
        service.aiGeneratedEvents.value.shouldBeEmpty()
    }
})
