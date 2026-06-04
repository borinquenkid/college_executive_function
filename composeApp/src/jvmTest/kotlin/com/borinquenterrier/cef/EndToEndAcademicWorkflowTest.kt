package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import com.russhwolf.settings.MapSettings
import com.borinquenterrier.cef.db.DriverFactory
import kotlinx.coroutines.flow.first
import io.mockk.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Verifies the full academic happy path using Mocks for AIService
 * to ensure deterministic logic verification:
 * 1. Add Academic Calendar (.ics)
 * 2. Add Course Syllabus (.pdf)
 * 3. Add Project Rubric (.docx)
 * 4. Generate Study Plan respecting all constraints.
 */
class EndToEndAcademicWorkflowTest : FunSpec({

    test("Full End-to-End Academic Happy Path (Mocked AI)") {
        // --- 1. SETUP ENVIRONMENT ---
        val settings = MapSettings()
        // Force 'test' profile to use Mock Calendar logic
        settings.putString("run_profile", "test")
        
        val logger = Logger(settings)
        val driverFactory = DriverFactory()
        
        // Mock AIService for deterministic results
        val mockAi = mockk<AIService>(relaxed = true)
        
        val container = DependencyContainer(
            settings = settings,
            logger = logger,
            driverFactory = driverFactory,
            modelBasePath = "/tmp/cef_models",
            fileReader = mockk(relaxed = true),
            docxReader = mockk(relaxed = true),
            pdfReader = mockk(relaxed = true)
        )
        
        // Inject our mock AI into the agents
        val ingestion = IngestionAgent(
            container.fileReader, container.docxReader, container.pdfReader, 
            container.webReader, container.driveService, mockAi, container.database
        )
        val calendar = container.calendarAgent
        val events = EventAgent(mockAi, calendar, container.database, NormalizationService(), logger = logger)
        val context = ContextAgent(mockAi, container.database, logger)

        // Clear existing state for a clean test run
        container.database.appDatabaseQueries.deleteAllEvents()
        container.database.appDatabaseQueries.deleteAllSources()

        // --- 2. STEP 1: ADD ACADEMIC CALENDAR (Holidays) ---
        val icsContent = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Labor Day Holiday
            DTSTART:20260907T090000
            DTEND:20260907T100000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        
        coEvery { container.fileReader.readText("academic_cal.ics") } returns icsContent
        val holidayEvent = DayEvent(
            title = "Labor Day Holiday",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.HOLIDAY,
            date = LocalDate(2026, 9, 7)
        )
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(holidayEvent)
        
        val calItem = ingestion.addLocalFile("academic_cal.ics")
        calItem.fragments.size shouldBe 1
        
        // Save to calendar
        calendar.saveEvent(holidayEvent)

        // --- 3. STEP 2: ADD COURSE SYLLABUS ---
        val midtermEvent = TimeEvent(
            title = "Midterm Exam",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.FINALS,
            date = LocalDate(2026, 10, 14),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(12, 0)
        )
        coEvery { mockAi.generateCalendarEvents(any()) } returns listOf(midtermEvent)
        
        val syllabusItem = SourceItem("Syllabus.pdf", listOf(SourceFragment("Midterm Oct 14 10am")))
        events.extractDeliverables(syllabusItem)
        events.lastGeneratedEvents.value.size shouldBe 1
        
        // --- 4. STEP 3: ACCEPT EXAM INTO CALENDAR ---
        // This is a critical step: the user must "Accept" (push) the deliverables
        // so they exist in the repository context for the Study Plan generator.
        events.pushToCalendar()
        calendar.getEvents().any { it.title.contains("Midterm") } shouldBe true

        // --- 5. STEP 4: PERFORM CONTEXT ANALYSIS ---
        coEvery { mockAi.analyzeDocument(any()) } returns "{\"late_policy\": \"10% per day\"}"
        context.analyzeSource(syllabusItem)
        
        context.getSourceMetadata("Syllabus.pdf")?.contains("10%") shouldBe true

        // --- 6. STEP 5: GENERATE STUDY PLAN ---
        val studyBlock = TimeEvent(
            title = "Study for Midterm",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 10, 13),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(16, 0)
        )
        // Verify that existing schedule (holiday + midterm) is passed to the AI
        val capturedSchedule = slot<String>()
        coEvery { mockAi.generateStudyPlan(any(), capture(capturedSchedule)) } returns listOf(studyBlock)
        
        events.generateStudyPlan(syllabusItem)
        
        println("CAPTURED SCHEDULE: ${capturedSchedule.captured}")
        
        capturedSchedule.captured.contains("Labor Day") shouldBe true
        capturedSchedule.captured.contains("Midterm Exam") shouldBe true
        events.lastGeneratedEvents.value.size shouldBe 1

        // --- 7. STEP 6: VERIFY CHAT Q&A ---
        coEvery { mockAi.generateChatResponse(any()) } returns "The late penalty is 10%."
        val answer = context.querySource(syllabusItem, "What is the penalty?")
        answer shouldBe "The late penalty is 10%."

        println("Mocked E2E Workflow Verified.")
    }
})
