package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.russhwolf.settings.MapSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposeUiFlowsTest {

    private fun createMockContainer(): DependencyContainer {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceLoader = mockk<SourceLoader>(relaxed = true)
        val mockSourceAdder = mockk<SourceAdder>(relaxed = true)
        val mockSourceRepository = mockk<SqlDelightSourceRepository>(relaxed = true)
        val mockLocalRepository = mockk<SqlDelightLocalCalendarRepository>(relaxed = true)
        val mockLogger = mockk<Logger>(relaxed = true)
        val realSourceSelector = SourceSelector()

        coEvery { mockSourceLoader.loadSources() } returns emptyList()
        coEvery { mockSourceRepository.getAllSources() } returns emptyList()
        coEvery { mockSourceRepository.getFragmentsForSource(any()) } returns emptyList()

        val realSourceDeleter = SourceDeleter(
            mockSourceRepository,
            mockLocalRepository,
            mockk(relaxed = true),
            mockLogger,
            GlobalScope
        )

        val realSourceManager = SourceManager(
            mockSourceLoader,
            mockSourceAdder,
            realSourceDeleter,
            realSourceSelector,
            GlobalScope
        )
        every { mockContainer.sourceManager } returns realSourceManager
        every { mockContainer.sourceRepository } returns mockSourceRepository
        every { mockContainer.localRepository } returns mockLocalRepository
        every { mockContainer.logger } returns mockLogger

        return mockContainer
    }

    @Test
    fun testSettingsScreenApiKeyRoundTrip() = runComposeUiTest {
        val settings = MapSettings()
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        every { mockContainer.settings } returns settings
        every { mockContainer.preferencesRepository } returns PreferencesRepository(settings)
        val mockGoogleFlow = mockk<GoogleAccountFlow>(relaxed = true)
        every { mockGoogleFlow.state } returns MutableStateFlow(GoogleConnectionState.Unlinked)
        every { mockContainer.googleAccountFlow } returns mockGoogleFlow

        setContent {
            SettingsScreen(container = mockContainer)
        }

        // Verify elements exist
        val inputNode = onNodeWithTag("settings_api_key_input")
        inputNode.assertExists()

        // 1. Save (TextInput)
        inputNode.performTextInput("AIzaSyTestKey123")
        settings.getString("CEF_GEMINI_API_KEY", "") shouldBe "AIzaSyTestKey123"

        // 2. Clear
        val clearButton = onNodeWithTag("settings_api_key_clear_button")
        clearButton.assertExists()
        clearButton.performClick()

        // Verify it was cleared
        settings.getString("CEF_GEMINI_API_KEY", "") shouldBe ""
    }

    @Test
    fun testChatPanelMessageSubmissionAndResponse() = runComposeUiTest {
        val mockContainer = createMockContainer()
        val mockContextAgent = mockk<ContextAgent>(relaxed = true)
        every { mockContainer.contextAgent } returns mockContextAgent

        val appController = AppController(mockContainer)

        // Mock queryAllSources to return a specific reply
        coEvery {
            mockContextAgent.queryAllSources(any(), any(), "Hello AI")
        } returns "This is the mocked response"

        setContent {
            ChatPanel(appController = appController)
        }

        // Verify elements exist
        val inputField = onNodeWithTag("chat_input_field")
        val sendButton = onNodeWithTag("chat_send_button")

        inputField.assertExists()
        sendButton.assertExists()

        // Type query
        inputField.performTextInput("Hello AI")

        // Click send
        sendButton.performClick()

        // Wait for AI response to be added to chat messages
        waitUntil(timeoutMillis = 5000L) {
            appController.chatMessages.value.any { it.author == "AI" && it.content == "This is the mocked response" }
        }

        // Verify the response is visible/added
        appController.chatMessages.value.size shouldBe 3 // Initial, User message, AI message
        appController.chatMessages.value[1].content shouldBe "Hello AI"
        appController.chatMessages.value[2].content shouldBe "This is the mocked response"
    }

    @Test
    fun testTaskDecompositionDialogStateProgression() = runComposeUiTest {
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        val decomposedTasksFlow = MutableStateFlow<List<DecomposedTask>>(emptyList())
        val isLoadingFlow = MutableStateFlow(false)
        val statusMessageFlow = MutableStateFlow("")

        every { mockEventAgent.decomposedTasks } returns decomposedTasksFlow
        every { mockEventAgent.isLoading } returns isLoadingFlow
        every { mockEventAgent.statusMessage } returns statusMessageFlow

        val event = DayEvent(
            id = "test-deadline",
            title = "Final Research Paper",
            source = EventSource.MANUAL,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 12, 1)
        )

        var dismissed = false
        val onDismiss = { dismissed = true }

        setContent {
            TaskDecompositionDialog(
                event = event,
                eventAgent = mockEventAgent,
                onDismiss = onDismiss
            )
        }

        // 1. Idle state: "Break It Down (AI)" button should exist, and loading indicator/add steps should not
        val breakButton = onNodeWithTag("break_it_down_button")
        breakButton.assertExists()
        onNodeWithTag("loading_indicator").assertDoesNotExist()
        onNodeWithTag("add_steps_button").assertDoesNotExist()

        // 2. Click "Break It Down" -> simulates triggering task decomposition
        breakButton.performClick()
        coVerify { mockEventAgent.decomposeTask(event) }

        // 3. Loading state: set isLoading to true
        isLoadingFlow.value = true
        onNodeWithTag("loading_indicator").assertExists()
        onNodeWithTag("break_it_down_button").assertDoesNotExist()

        // 4. Results state: set isLoading to false and add decomposed tasks
        val mockTasks = listOf(
            DecomposedTask("Task 1", 7, "Choose research topic"),
            DecomposedTask("Task 2", 3, "Draft introduction")
        )
        isLoadingFlow.value = false
        decomposedTasksFlow.value = mockTasks

        // Now "Add Steps to Calendar" button should be visible, and "Break It Down" should not
        onNodeWithTag("loading_indicator").assertDoesNotExist()
        onNodeWithTag("break_it_down_button").assertDoesNotExist()
        val addStepsButton = onNodeWithTag("add_steps_button")
        addStepsButton.assertExists()

        // 5. Accepted state: click "Add Steps"
        addStepsButton.performClick()
        coVerify { mockEventAgent.acceptDecomposition() }
        dismissed shouldBe true
    }

    @Test
    fun testSyncNegotiationDialogProposalsAndAcceptance() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)

        val originalEvent = DayEvent(
            id = "study-1",
            title = "Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 10, 1)
        )
        val proposedEvent = DayEvent(
            id = "study-1",
            title = "Study Block",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = LocalDate(2026, 10, 2)
        )
        val collidingEvent = DayEvent(
            id = "exam-1",
            title = "PHYS 401 Midterm",
            source = EventSource.CLASS,
            category = AcademicCategory.CLASS,
            date = LocalDate(2026, 10, 1)
        )

        val proposal = SyncProposal.StudyBlockShift(
            originalEvent = originalEvent,
            proposedEvent = proposedEvent,
            collidingEvent = collidingEvent
        )

        val negotiation = SyncNegotiation(
            proposals = listOf(proposal),
            remoteEventsToSync = emptyList(),
            deletedLocalIds = emptyList()
        )

        var applied = false
        var dismissed = false

        setContent {
            SyncNegotiationDialog(
                negotiation = negotiation,
                calendarAgent = mockCalendarAgent,
                onApplied = { applied = true },
                onDismiss = { dismissed = true }
            )
        }

        // Verify elements exist and display texts
        onNodeWithText("Google Calendar Sync Proposals").assertExists()
        onNodeWithText("Shift Study Block").assertExists()
        onNodeWithText(proposal.description).assertExists()

        // 1. Test Acceptance
        val acceptButton = onNodeWithText("Accept Proposal")
        acceptButton.assertExists()
        acceptButton.performClick()

        coVerify { mockCalendarAgent.applySyncNegotiation(negotiation) }
        applied shouldBe true

        // 2. Test Dismissal (reset flag and click cancel)
        val cancelButton = onNodeWithText("Cancel")
        cancelButton.assertExists()
        cancelButton.performClick()
        dismissed shouldBe true
    }

    @Test
    fun testAcademicCalendarRendering() = runComposeUiTest {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)

        val errorStateFlow = MutableStateFlow<AgentError?>(null)
        every { mockEventAgent.errorState } returns errorStateFlow

        // Mock getEvents to return a list of events
        val testEvents = listOf(
            DayEvent(
                id = "event-1",
                title = "Calculus HW 3",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 12)
            ),
            DayEvent(
                id = "event-2",
                title = "Physics Lecture",
                source = EventSource.ROUTINE,
                category = AcademicCategory.REGULAR,
                date = LocalDate(2026, 6, 13)
            )
        )
        coEvery { mockCalendarAgent.getEvents(any()) } returns testEvents

        var navigatedScreen: AppScreen? = null
        val onNavigate: (AppScreen) -> Unit = { navigatedScreen = it }

        setContent {
            AcademicCalendar(
                aiGeneratedEvents = emptyList(),
                calendarAgent = mockCalendarAgent,
                eventAgent = mockEventAgent,
                onNavigate = onNavigate
            )
        }

        // Verify that elements from the calendar are rendered
        onNodeWithText("Calculus HW 3").assertExists()
        onNodeWithText("Physics Lecture").assertExists()
        onNodeWithText("Important Deadline").assertExists()
        onNodeWithText("Weekly Routine").assertExists()
        onNodeWithText("Add Source").assertExists()

        // Click on "Weekly Routine" button and verify navigation to Routine screen
        val routineButton = onNodeWithText("Weekly Routine")
        routineButton.assertExists()
        routineButton.performClick()
        navigatedScreen shouldBe AppScreen.Routine
    }

    @Test
    fun testSettingsCreateGoogleCalendarDialog() = runComposeUiTest {
        val settings = MapSettings()
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val preferencesRepository = PreferencesRepository(settings)
        every { mockContainer.settings } returns settings
        every { mockContainer.preferencesRepository } returns preferencesRepository

        val mockGoogleFlow = mockk<GoogleAccountFlow>(relaxed = true)
        every { mockGoogleFlow.state } returns MutableStateFlow(GoogleConnectionState.Linked)
        every { mockContainer.googleAccountFlow } returns mockGoogleFlow

        val mockSyncService = mockk<GoogleCalendarSyncService>(relaxed = true)
        every { mockContainer.syncService } returns mockSyncService
        coEvery { mockSyncService.createCalendar("New Test Calendar") } returns "new-cal-123"

        val mockRemoteRepo = mockk<GoogleRemoteCalendarRepository>(relaxed = true)
        every { mockContainer.remoteRepository } returns mockRemoteRepo
        coEvery { mockRemoteRepo.getAvailableCalendars() } returns listOf(
            RemoteCalendarMetadata("new-cal-123", "New Test Calendar")
        )

        setContent {
            SettingsScreen(container = mockContainer)
        }

        // Verify button exists and click it
        val createBtn = onNodeWithTag("settings_create_calendar_button")
        createBtn.assertExists()
        createBtn.performClick()

        // Verify dialog and input field exist
        val nameInput = onNodeWithTag("create_calendar_name_input")
        nameInput.assertExists()
        nameInput.performTextInput("New Test Calendar")

        // Click Create button on dialog
        val createConfirmBtn = onNodeWithText("Create")
        createConfirmBtn.assertExists()
        createConfirmBtn.performClick()

        // Wait for preference storage to be updated asynchronously
        waitUntil(timeoutMillis = 5000L) {
            val jsonStr = settings.getString("STUDY_PREFERENCES", "")
            if (jsonStr.isBlank()) return@waitUntil false
            try {
                val prefs =
                    kotlinx.serialization.json.Json.decodeFromString<StudyPreferences>(jsonStr)
                prefs.googleCalendarId == "new-cal-123"
            } catch (e: Exception) {
                false
            }
        }

        // Verify API calls
        coVerify { mockSyncService.createCalendar("New Test Calendar") }
        coVerify { mockRemoteRepo.getAvailableCalendars() }

        // Verify preference storage was updated
        val jsonStr = settings.getString("STUDY_PREFERENCES", "")
        val prefs = kotlinx.serialization.json.Json.decodeFromString<StudyPreferences>(jsonStr)
        prefs.googleCalendarName shouldBe "New Test Calendar"
    }
}
