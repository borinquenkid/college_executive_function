package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
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
        every { mockEventAgent.persistedWarnings } returns MutableStateFlow(emptyList())
        every { mockEventAgent.errorState } returns MutableStateFlow(null)
        every { mockEventAgent.extractionWarning } returns MutableStateFlow(null)
        every { mockEventAgent.pendingRequestCount } returns MutableStateFlow(0)

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
