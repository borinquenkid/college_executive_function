package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import com.russhwolf.settings.MapSettings
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.DriverFactory
import kotlinx.coroutines.runBlocking
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
        container.googleAccountFlow.error.value shouldBe null
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
        
        val eventAgent = EventAgent(mockAiService, mockCalendarAgent, database, NormalizationService(), logger)
        
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
        coEvery { mockAiService.generateStudyPlan(any(), capture(scheduleSlot)) } returns mockAiResponse
        
        // 3. Run generateStudyPlan
        val source = SourceItem("Mock Syllabus", listOf(SourceFragment("Midterm Exam on Oct 15", type = SourceType.TEXT)))
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
})
