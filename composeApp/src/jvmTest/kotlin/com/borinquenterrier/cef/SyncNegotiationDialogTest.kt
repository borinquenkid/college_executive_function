package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UI regression tests for SyncNegotiationDialog — the "Accept Proposal" dialog shown
 * when Google Calendar sync detects conflicts with local study blocks.
 *
 * Why these were missing: neither StudioPanel tests nor AcademicCalendar tests covered
 * the dialog. The "Accept Proposal" button had no tests verifying it actually called
 * applySyncNegotiation or that onApplied was invoked.
 */
@OptIn(ExperimentalTestApi::class)
class SyncNegotiationDialogTest {

    private fun mockCalendarAgent(): CalendarAgent {
        val agent = mockk<CalendarAgent>(relaxed = true)
        every { agent.resetVersion } returns MutableStateFlow(0)
        return agent
    }

    private fun aDirectConflict(): SyncProposal.DirectConflict {
        val event = DayEvent(
            id = "e1",
            title = "Midterm Exam",
            date = LocalDate(2024, 3, 15),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR
        )
        return SyncProposal.DirectConflict(localEvent = event, remoteEvent = event)
    }

    private fun aStudyBlockShift(): SyncProposal.StudyBlockShift {
        val date = LocalDate(2024, 3, 15)
        val original = TimeEvent(
            id = "s1",
            title = "Study Session",
            date = date,
            startTime = LocalTime(14, 0),
            endTime = LocalTime(16, 0),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )
        val proposed = original.copy(
            startTime = LocalTime(16, 0),
            endTime = LocalTime(18, 0)
        )
        val colliding = DayEvent(
            id = "c1",
            title = "Lab",
            date = date,
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.REGULAR
        )
        return SyncProposal.StudyBlockShift(originalEvent = original, proposedEvent = proposed, collidingEvent = colliding)
    }

    private fun emptyNegotiation() = SyncNegotiation(
        proposals = emptyList(),
        remoteEventsToSync = emptyList(),
        deletedLocalIds = emptyList()
    )

    // ── Dialog structure ─────────────────────────────────────────────────────

    @Test
    fun dialogShowsTitle() = runComposeUiTest {
        setContent {
            SyncNegotiationDialog(
                negotiation = emptyNegotiation(),
                calendarAgent = mockCalendarAgent(),
                onApplied = {},
                onDismiss = {}
            )
        }

        onNodeWithText("Google Calendar Sync Proposals").assertExists()
    }

    @Test
    fun dialogShowsDirectConflictProposal() = runComposeUiTest {
        val negotiation = SyncNegotiation(
            proposals = listOf(aDirectConflict()),
            remoteEventsToSync = emptyList(),
            deletedLocalIds = emptyList()
        )

        setContent {
            SyncNegotiationDialog(
                negotiation = negotiation,
                calendarAgent = mockCalendarAgent(),
                onApplied = {},
                onDismiss = {}
            )
        }

        onNodeWithText("Conflicting Edits").assertExists()
    }

    @Test
    fun dialogShowsStudyBlockShiftProposal() = runComposeUiTest {
        val negotiation = SyncNegotiation(
            proposals = listOf(aStudyBlockShift()),
            remoteEventsToSync = emptyList(),
            deletedLocalIds = emptyList()
        )

        setContent {
            SyncNegotiationDialog(
                negotiation = negotiation,
                calendarAgent = mockCalendarAgent(),
                onApplied = {},
                onDismiss = {}
            )
        }

        onNodeWithText("Shift Study Block").assertExists()
    }

    @Test
    fun acceptAndCancelButtonsExistWithCorrectTags() = runComposeUiTest {
        setContent {
            SyncNegotiationDialog(
                negotiation = emptyNegotiation(),
                calendarAgent = mockCalendarAgent(),
                onApplied = {},
                onDismiss = {}
            )
        }

        onNodeWithTag("accept_proposal_button").assertExists()
        onNodeWithTag("cancel_sync_button").assertExists()
        onNodeWithText("Accept Proposal").assertExists()
        onNodeWithText("Cancel").assertExists()
    }

    // ── Accept Proposal button ───────────────────────────────────────────────

    @Test
    fun acceptProposalCallsApplySyncNegotiation() = runComposeUiTest {
        val agent = mockCalendarAgent()
        coEvery { agent.applySyncNegotiation(any(), any()) } just Runs

        val negotiation = SyncNegotiation(
            proposals = listOf(aStudyBlockShift()),
            remoteEventsToSync = emptyList(),
            deletedLocalIds = emptyList()
        )

        setContent {
            SyncNegotiationDialog(
                negotiation = negotiation,
                calendarAgent = agent,
                onApplied = {},
                onDismiss = {}
            )
        }

        onNodeWithTag("accept_proposal_button").performClick()
        waitForIdle()

        coVerify { agent.applySyncNegotiation(negotiation) }
    }

    @Test
    fun acceptProposalInvokesOnAppliedCallback() = runComposeUiTest {
        var applied = false
        val agent = mockCalendarAgent()
        coEvery { agent.applySyncNegotiation(any(), any()) } just Runs

        setContent {
            SyncNegotiationDialog(
                negotiation = emptyNegotiation(),
                calendarAgent = agent,
                onApplied = { applied = true },
                onDismiss = {}
            )
        }

        onNodeWithTag("accept_proposal_button").performClick()
        waitForIdle()

        assertTrue(applied, "onApplied must be called after accepting the proposal")
    }

    @Test
    fun acceptProposalStillCallsOnAppliedWhenSyncThrows() = runComposeUiTest {
        var applied = false
        val agent = mockCalendarAgent()
        coEvery { agent.applySyncNegotiation(any(), any()) } throws RuntimeException("Network error")

        setContent {
            SyncNegotiationDialog(
                negotiation = emptyNegotiation(),
                calendarAgent = agent,
                onApplied = { applied = true },
                onDismiss = {}
            )
        }

        onNodeWithTag("accept_proposal_button").performClick()
        waitForIdle()

        // Dialog always dismisses so user isn't stuck; onApplied is the dismiss signal
        assertTrue(applied, "onApplied must be called even when applySyncNegotiation throws")
    }

    // ── Cancel button ────────────────────────────────────────────────────────

    @Test
    fun cancelButtonInvokesOnDismissWithoutCallingApply() = runComposeUiTest {
        var dismissed = false
        val agent = mockCalendarAgent()

        setContent {
            SyncNegotiationDialog(
                negotiation = emptyNegotiation(),
                calendarAgent = agent,
                onApplied = {},
                onDismiss = { dismissed = true }
            )
        }

        onNodeWithTag("cancel_sync_button").performClick()
        waitForIdle()

        assertTrue(dismissed, "onDismiss must fire when Cancel is clicked")
        coVerify(exactly = 0) { agent.applySyncNegotiation(any(), any()) }
    }

    @Test
    fun cancelButtonDoesNotInvokeOnApplied() = runComposeUiTest {
        var applied = false
        val agent = mockCalendarAgent()

        setContent {
            SyncNegotiationDialog(
                negotiation = emptyNegotiation(),
                calendarAgent = agent,
                onApplied = { applied = true },
                onDismiss = {}
            )
        }

        onNodeWithTag("cancel_sync_button").performClick()
        waitForIdle()

        assertFalse(applied, "onApplied must NOT be called when Cancel is clicked")
    }
}
