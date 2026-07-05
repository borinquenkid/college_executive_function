package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.GlobalScope
import kotlin.test.Test

/**
 * HARD-9: "Disconnect Account" must not tear down the Google connection silently — it
 * needs an explicit confirmation step, and confirming must not touch local event data
 * (disconnect only removes the connection; previously-synced events stay on-device).
 */
@OptIn(ExperimentalTestApi::class)
class GoogleCalendarPanelTest {

    private fun buildMockContainer(): Pair<DependencyContainer, GoogleAccountFlow> {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockGoogleFlow = mockk<GoogleAccountFlow>(relaxed = true)
        every { mockContainer.googleAccountFlow } returns mockGoogleFlow
        return mockContainer to mockGoogleFlow
    }

    private fun panelContent(container: DependencyContainer): @androidx.compose.runtime.Composable () -> Unit = {
        GoogleCalendarPanel(
            container = container,
            isGoogleLinked = true,
            isBusy = false,
            loginError = null,
            googleCalendarId = "default",
            googleCalendarName = "CEF Academic",
            calendars = emptyList(),
            isLoadingCalendars = false,
            calendarLoadError = null,
            onCalendarIdChange = { _, _ -> },
            onCalendarsRefresh = {},
            onCalendarLoadError = {},
            scope = GlobalScope
        )
    }

    @Test
    fun clickingDisconnectShowsConfirmationInsteadOfDisconnectingImmediately() = runComposeUiTest {
        val (container, googleFlow) = buildMockContainer()
        setContent(panelContent(container))

        onNodeWithText("Disconnect Account").performClick()

        verify(exactly = 0) { googleFlow.disconnect() }
        onNodeWithText("Disconnect Google Calendar?").assertExists()
    }

    @Test
    fun cancellingTheConfirmationDoesNotDisconnect() = runComposeUiTest {
        val (container, googleFlow) = buildMockContainer()
        setContent(panelContent(container))

        onNodeWithText("Disconnect Account").performClick()
        onNodeWithText("Cancel").performClick()

        verify(exactly = 0) { googleFlow.disconnect() }
        onNodeWithText("Disconnect Google Calendar?").assertDoesNotExist()
    }

    @Test
    fun confirmingDisconnectCallsGoogleAccountFlowDisconnect() = runComposeUiTest {
        val (container, googleFlow) = buildMockContainer()
        setContent(panelContent(container))

        onNodeWithText("Disconnect Account").performClick()
        onNodeWithText("Disconnect").performClick()

        verify(exactly = 1) { googleFlow.disconnect() }
    }
}
