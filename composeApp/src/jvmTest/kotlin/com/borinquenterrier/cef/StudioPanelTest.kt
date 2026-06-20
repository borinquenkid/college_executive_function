package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StudioPanelTest {

    @Test
    fun testStudioPanelNullSource() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns MutableStateFlow(false)
        every { mockContainer.tokenRepository } returns tokenRepo

        // Mock states
        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Select a source and an action.")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(emptyList())
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)

        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()

        setContent {
            StudioPanel(
                selectedSource = null,
                calendarAgent = mockCalendarAgent,
                container = mockContainer,
                onEventsGenerated = {}
            )
        }

        // Verify that the no-source placeholder exists
        onNodeWithTag("no_source_placeholder").assertExists()
        onNodeWithText("Select a source to get started.").assertExists()

        // Verify that action buttons do NOT exist
        onNodeWithTag("process_syllabus_button").assertDoesNotExist()
        onNodeWithTag("push_calendar_button").assertDoesNotExist()
        onNodeWithTag("export_ics_button").assertDoesNotExist()
    }

    @Test
    fun testStudioPanelWithSourceAndSyllabusProcessing() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent

        val mockAppController = mockk<AppController>(relaxed = true)
        every { mockContainer.appController } returns mockAppController
        every { mockAppController.launchInScope(any()) } answers {
            val block = firstArg<suspend kotlinx.coroutines.CoroutineScope.() -> Unit>()
            kotlinx.coroutines.runBlocking { block(this) }
        }

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns MutableStateFlow(false)
        every { mockContainer.tokenRepository } returns tokenRepo

        // Mock states
        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Ready")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(emptyList())
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)

        val testSource = SourceItem(
            title = "Test Syllabus",
            fragments = listOf(SourceFragment(text = "Course requirements")),
            category = SourceCategory.SYLLABUS
        )

        setContent {
            StudioPanel(
                selectedSource = testSource,
                calendarAgent = mockCalendarAgent,
                container = mockContainer,
                onEventsGenerated = {}
            )
        }

        // Placeholder should not exist
        onNodeWithTag("no_source_placeholder").assertDoesNotExist()

        // Button should exist
        val processButton = onNodeWithTag("process_syllabus_button")
        processButton.assertExists()
        processButton.performClick()

        // Verify generateStudyPlan was called on eventAgent
        coVerify { mockEventAgent.generateStudyPlan(testSource) }
    }

    @Test
    fun testStudioPanelDisplayEventsAndPush() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent

        val mockAppController = mockk<AppController>(relaxed = true)
        every { mockContainer.appController } returns mockAppController
        every { mockAppController.launchInScope(any()) } answers {
            val block = firstArg<suspend kotlinx.coroutines.CoroutineScope.() -> Unit>()
            kotlinx.coroutines.runBlocking { block(this) }
        }

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        // Set connected/linked to true
        every { tokenRepo.isLinked } returns MutableStateFlow(true)
        every { mockContainer.tokenRepository } returns tokenRepo

        val generatedEvents = listOf(
            DayEvent(
                id = "test-event-1",
                title = "Calculus Homework 1",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 15),
                warning = "Due soon"
            )
        )

        // Mock states
        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Sync pending")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(generatedEvents)
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)
        every { mockEventAgent.unresolvedConflicts } returns MutableStateFlow(emptyList())

        val testSource = SourceItem(
            title = "Test Syllabus",
            fragments = listOf(SourceFragment(text = "Course requirements")),
            category = SourceCategory.SYLLABUS
        )

        var onEventsGeneratedCalled = false
        setContent {
            StudioPanel(
                selectedSource = testSource,
                calendarAgent = mockCalendarAgent,
                container = mockContainer,
                onEventsGenerated = { onEventsGeneratedCalled = true }
            )
        }

        // Verify event displays
        onNodeWithText("Calculus Homework 1").assertExists()
        onNodeWithText("2026-06-15").assertExists()

        // Verify warnings card shows with deduplicated warning text (not repeated per event)
        onNodeWithTag("source_discrepancies_card").assertExists()
        onNodeWithText("Source Notes").assertExists()
        onNodeWithText("- Due soon").assertExists()
        onNodeWithText("- Calculus Homework 1: Due soon").assertDoesNotExist()

        // Verify push button exists and click triggers push
        val pushButton = onNodeWithTag("push_calendar_button")
        pushButton.assertExists()
        onNodeWithText("Push to Google Calendar").assertExists()
        pushButton.performClick()

        coVerify { mockEventAgent.pushToCalendar() }

        // Verify export button exists
        onNodeWithTag("export_ics_button").assertExists()
    }

    @Test
    fun testStudioPanelLoadingState() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns MutableStateFlow(false)
        every { mockContainer.tokenRepository } returns tokenRepo

        // Mock states
        every { mockEventAgent.isLoading } returns MutableStateFlow(true)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Processing...")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(emptyList())
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)

        val testSource = SourceItem(
            title = "Test Syllabus",
            fragments = listOf(SourceFragment(text = "Course requirements")),
            category = SourceCategory.SYLLABUS
        )

        setContent {
            StudioPanel(
                selectedSource = testSource,
                calendarAgent = mockCalendarAgent,
                container = mockContainer,
                onEventsGenerated = {}
            )
        }

        // Verify progress indicator and status text are both visible in loading state
        onNodeWithTag("studio_loading_indicator").assertExists()
        onNodeWithTag("status_message_text").assertExists()
        onNodeWithText("Processing...").assertExists()

        // Verify process button is disabled
        onNodeWithTag("process_syllabus_button").assertIsNotEnabled()
    }

    @Test
    fun testSourceNotesDeduplicateWarnings() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent
        every { mockContainer.appController } returns mockk(relaxed = true)

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns MutableStateFlow(false)
        every { mockContainer.tokenRepository } returns tokenRepo

        // All 4 events share the identical warning (like STLCC source audit stamps on every event)
        val sharedWarning = "[TENTATIVE] Schedule subject to change; [EXTERNAL_LMS] Grades on Canvas"
        val generatedEvents = listOf(
            DayEvent(id = "e1", title = "Issue Brief #1", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 1), warning = sharedWarning),
            DayEvent(id = "e2", title = "Issue Brief #2", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 15), warning = sharedWarning),
            DayEvent(id = "e3", title = "Issue Brief #3", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 22), warning = sharedWarning),
            DayEvent(id = "e4", title = "Final Paper", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 31), warning = sharedWarning),
        )

        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Ready")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(generatedEvents)
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)
        every { mockEventAgent.unresolvedConflicts } returns MutableStateFlow(emptyList())

        val testSource = SourceItem(
            title = "ENG 101 Syllabus",
            fragments = listOf(SourceFragment(text = "text")),
            category = SourceCategory.SYLLABUS
        )

        setContent {
            StudioPanel(selectedSource = testSource, calendarAgent = mockCalendarAgent,
                container = mockContainer, onEventsGenerated = {})
        }

        onNodeWithTag("source_discrepancies_card").assertExists()
        // Warning appears exactly once, not repeated 4× with event titles
        onNodeWithText("- $sharedWarning").assertExists()
        onNodeWithText("- Issue Brief #1: $sharedWarning").assertDoesNotExist()
        onNodeWithText("- Issue Brief #2: $sharedWarning").assertDoesNotExist()
    }

    @Test
    fun testSourceNotesShowsDistinctWarningsSeparately() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent
        every { mockContainer.appController } returns mockk(relaxed = true)

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns MutableStateFlow(false)
        every { mockContainer.tokenRepository } returns tokenRepo

        val generatedEvents = listOf(
            DayEvent(id = "e1", title = "Assignment A", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 1),
                warning = "[TENTATIVE] Dates may shift"),
            DayEvent(id = "e2", title = "Assignment B", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 7, 15),
                warning = "[EXTERNAL_LMS] Details on Canvas"),
        )

        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Ready")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(generatedEvents)
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)
        every { mockEventAgent.unresolvedConflicts } returns MutableStateFlow(emptyList())

        val testSource = SourceItem(
            title = "Test Syllabus",
            fragments = listOf(SourceFragment(text = "text")),
            category = SourceCategory.SYLLABUS
        )

        setContent {
            StudioPanel(selectedSource = testSource, calendarAgent = mockCalendarAgent,
                container = mockContainer, onEventsGenerated = {})
        }

        onNodeWithTag("source_discrepancies_card").assertExists()
        onNodeWithText("- [TENTATIVE] Dates may shift").assertExists()
        onNodeWithText("- [EXTERNAL_LMS] Details on Canvas").assertExists()
    }

    @Test
    fun testStudioPanelExportIcs() = runComposeUiTest {
        // Try mocking the package-level helper class from multiplatform
        mockkStatic("com.borinquenterrier.cef.IcsExport_jvmKt")

        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent

        val mockAppController = mockk<AppController>(relaxed = true)
        every { mockContainer.appController } returns mockAppController
        every { mockAppController.launchInScope(any()) } answers {
            val block = firstArg<suspend kotlinx.coroutines.CoroutineScope.() -> Unit>()
            kotlinx.coroutines.runBlocking { block(this) }
        }

        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)
        every { tokenRepo.isLinked } returns MutableStateFlow(false)
        every { mockContainer.tokenRepository } returns tokenRepo

        val generatedEvents = listOf(
            DayEvent(
                id = "test-event-1",
                title = "Calculus Homework 1",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 15)
            )
        )

        // Mock states
        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Ready")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(generatedEvents)
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)
        every { mockEventAgent.unresolvedConflicts } returns MutableStateFlow(emptyList())

        val testSource = SourceItem(
            title = "Test Syllabus",
            fragments = listOf(SourceFragment(text = "Course requirements")),
            category = SourceCategory.SYLLABUS
        )

        setContent {
            StudioPanel(
                selectedSource = testSource,
                calendarAgent = mockCalendarAgent,
                container = mockContainer,
                onEventsGenerated = {}
            )
        }

        val exportButton = onNodeWithTag("export_ics_button")
        exportButton.assertExists()
        exportButton.performClick()

        // Verify that the eventAgent is updated with status on export success or failure
        coVerify { mockEventAgent.updateStatus(any()) }
    }
}
