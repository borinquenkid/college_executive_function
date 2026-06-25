package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * UI regression tests for ConflictResolutionDialog (the "Got it" dialog shown when
 * immovable events — quiz, test, exam — require professor approval to reschedule).
 *
 * Why these were missing: StudioPanel tests mock unresolvedConflicts but never render
 * ConflictResolutionDialog directly. The dialog and its button had zero UI coverage.
 */
@OptIn(ExperimentalTestApi::class)
class ConflictResolutionDialogTest {

    private fun aConflict(title: String = "Midterm Exam") = ConflictResolver.UnresolvedConflict(
        title = title,
        date = LocalDate(2024, 3, 15),
        reason = "Cannot auto-reschedule QUIZ. Contact professor to reschedule.",
        requiresProfessorApproval = true
    )

    @Test
    fun dialogShowsConflictTitleAndDate() = runComposeUiTest {
        setContent {
            ConflictResolutionDialog(
                conflicts = listOf(aConflict("Midterm Exam")),
                onDismiss = {}
            )
        }

        onNodeWithText("Schedule Conflicts Detected").assertExists()
        onNodeWithText("Midterm Exam").assertExists()
        onNodeWithText("2024-03-15").assertExists()
    }

    @Test
    fun dialogShowsReasonText() = runComposeUiTest {
        setContent {
            ConflictResolutionDialog(
                conflicts = listOf(aConflict()),
                onDismiss = {}
            )
        }

        onNodeWithText("Cannot auto-reschedule QUIZ. Contact professor to reschedule.").assertExists()
    }

    @Test
    fun gotItButtonExistsAndIsTagged() = runComposeUiTest {
        setContent {
            ConflictResolutionDialog(
                conflicts = listOf(aConflict()),
                onDismiss = {}
            )
        }

        onNodeWithTag("conflict_got_it_button").assertExists()
        onNodeWithText("Got it").assertExists()
    }

    @Test
    fun clickingGotItInvokesOnDismiss() = runComposeUiTest {
        var dismissed = false

        setContent {
            ConflictResolutionDialog(
                conflicts = listOf(aConflict()),
                onDismiss = { dismissed = true }
            )
        }

        onNodeWithTag("conflict_got_it_button").performClick()

        assertTrue(dismissed, "onDismiss should be called when Got it is clicked")
    }

    @Test
    fun multipleConflictsAreAllDisplayed() = runComposeUiTest {
        val conflicts = listOf(
            aConflict("Quiz 1"),
            aConflict("Midterm Exam"),
            aConflict("Final Exam")
        )

        setContent {
            ConflictResolutionDialog(conflicts = conflicts, onDismiss = {})
        }

        onNodeWithText("Quiz 1").assertExists()
        onNodeWithText("Midterm Exam").assertExists()
        onNodeWithText("Final Exam").assertExists()
    }

    @Test
    fun dialogShowsNextStepsInstructions() = runComposeUiTest {
        setContent {
            ConflictResolutionDialog(conflicts = listOf(aConflict()), onDismiss = {})
        }

        onNodeWithText("📧 Next Steps:").assertExists()
    }
}
