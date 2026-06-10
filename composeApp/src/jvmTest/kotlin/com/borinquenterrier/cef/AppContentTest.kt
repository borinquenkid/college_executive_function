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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppContentTest {

    private fun buildMockContainer(
        incompleteEvents: MutableStateFlow<List<Event>> = MutableStateFlow(
            emptyList()
        )
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
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.incompleteEvents } returns incompleteEvents

        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()

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
}
