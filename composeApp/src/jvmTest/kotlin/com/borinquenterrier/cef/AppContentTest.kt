package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.russhwolf.settings.MapSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppContentTest {

    private fun buildMockContainer(
        incompleteEvents: MutableStateFlow<List<Event>> = MutableStateFlow(emptyList()),
        errorState: MutableStateFlow<AgentError?> = MutableStateFlow(null)
    ): DependencyContainer {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockEventAgent = mockk<EventAgent>(relaxed = true)
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        val tokenRepo = mockk<GoogleTokenRepository>(relaxed = true)

        every { mockContainer.eventAgent } returns mockEventAgent
        every { mockContainer.calendarAgent } returns mockCalendarAgent
        every { mockContainer.tokenRepository } returns tokenRepo
        every { tokenRepo.isLinked } returns MutableStateFlow(false)

        every { mockEventAgent.isLoading } returns MutableStateFlow(false)
        every { mockEventAgent.statusMessage } returns MutableStateFlow("Select a source and an action.")
        every { mockEventAgent.lastGeneratedEvents } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns errorState
        every { mockEventAgent.incompleteEvents } returns incompleteEvents

        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()

        // Set up sourceManager for AppController
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
            mockCalendarAgent,
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

        every { mockContainer.appController } returns AppController(mockContainer)

        // SettingsScreen dependencies (mirrors ComposeUiFlowsTest's settings round-trip setup)
        val settings = MapSettings()
        every { mockContainer.settings } returns settings
        every { mockContainer.preferencesRepository } returns PreferencesRepository(settings)
        val mockGoogleFlow = mockk<GoogleAccountFlow>(relaxed = true)
        every { mockGoogleFlow.state } returns MutableStateFlow(GoogleConnectionState.Unlinked)
        every { mockContainer.googleAccountFlow } returns mockGoogleFlow

        return mockContainer
    }

    @Test
    fun testNavBarSwitchesBetweenHomeAndSettings() = runComposeUiTest {
        val container = buildMockContainer()

        setContent { AppContent(container) }

        // Home is the default screen
        onNodeWithTag("chat_input_field").assertExists()
        onNodeWithTag("settings_api_key_input").assertDoesNotExist()

        onNodeWithTag("nav_settings_button").performClick()
        onNodeWithTag("settings_api_key_input").assertExists()
        onNodeWithTag("chat_input_field").assertDoesNotExist()

        onNodeWithTag("nav_home_button").performClick()
        onNodeWithTag("chat_input_field").assertExists()
        onNodeWithTag("settings_api_key_input").assertDoesNotExist()
    }

    @Test
    fun testCheckInDialogShowsForIncompleteEventsAndCanBeDismissed() = runComposeUiTest {
        val incompleteFlow = MutableStateFlow<List<Event>>(
            listOf(
                DayEvent(
                    id = "incomplete-1",
                    title = "Reading Response",
                    source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE,
                    date = kotlinx.datetime.LocalDate(2026, 6, 1)
                )
            )
        )
        val container = buildMockContainer(incompleteEvents = incompleteFlow)

        setContent { AppContent(container) }

        onNodeWithText("Startup Check-In").assertExists()

        onNodeWithText("Dismiss").performClick()

        onNodeWithText("Startup Check-In").assertDoesNotExist()
    }

    @Test
    fun testCheckInDialogHiddenWhenNoIncompleteEvents() = runComposeUiTest {
        val container = buildMockContainer()

        setContent { AppContent(container) }

        onNodeWithText("Startup Check-In").assertDoesNotExist()
    }

    @Test
    fun testQuotaErrorBannerAppearsOnHomeScreen() = runComposeUiTest {
        val errorFlow = MutableStateFlow<AgentError?>(null)
        val container = buildMockContainer(errorState = errorFlow)

        setContent { AppContent(container) }

        // On the Home screen (default), no banner yet
        onNodeWithText("Daily AI Quota Reached").assertDoesNotExist()

        // Simulate quota exhaustion from background harness
        errorFlow.value = AgentError.QuotaExhausted

        // Banner must appear without navigating away from Home
        onNodeWithText("Daily AI Quota Reached").assertExists()
    }

    @Test
    fun testQuotaErrorBannerAppearsOnSettingsScreen() = runComposeUiTest {
        val container = buildMockContainer(errorState = MutableStateFlow(AgentError.QuotaExhausted))

        setContent { AppContent(container) }

        // Navigate to Settings — banner must still be visible
        onNodeWithTag("nav_settings_button").performClick()
        onNodeWithText("Daily AI Quota Reached").assertExists()
    }
}
