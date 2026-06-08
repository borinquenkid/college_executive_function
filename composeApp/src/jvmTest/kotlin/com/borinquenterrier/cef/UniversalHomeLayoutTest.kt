package com.borinquenterrier.cef

import androidx.compose.ui.test.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class UniversalHomeLayoutTest {

    private fun buildMockContainer(): DependencyContainer {
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

        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()

        every { mockContainer.appController } returns AppController(mockContainer)

        return mockContainer
    }

    @Test
    fun testChatCanvasRendersByDefaultWithDrawersHidden() = runComposeUiTest {
        val container = buildMockContainer()

        setContent { UniversalHomeLayout(container) }

        onNodeWithTag("chat_input_field").assertExists()
        onNodeWithTag("sources_drawer").assertDoesNotExist()
        onNodeWithTag("studio_drawer").assertDoesNotExist()
    }

    @Test
    fun testTogglingDrawersIsMutuallyExclusive() = runComposeUiTest {
        val container = buildMockContainer()

        setContent { UniversalHomeLayout(container) }

        onNodeWithTag("sources_toggle_button").performClick()
        onNodeWithTag("sources_drawer").assertExists()
        onNodeWithTag("studio_drawer").assertDoesNotExist()

        onNodeWithTag("studio_toggle_button").performClick()
        onNodeWithTag("studio_drawer").assertExists()
        onNodeWithTag("studio_panel").assertExists()
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("sources_drawer").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun testToggleButtonClosesItsOwnDrawer() = runComposeUiTest {
        val container = buildMockContainer()

        setContent { UniversalHomeLayout(container) }

        onNodeWithTag("studio_toggle_button").performClick()
        onNodeWithTag("studio_drawer").assertExists()
        waitForIdle()

        onNodeWithTag("studio_toggle_button").performClick()
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("studio_drawer").fetchSemanticsNodes().isEmpty()
        }
    }
}
