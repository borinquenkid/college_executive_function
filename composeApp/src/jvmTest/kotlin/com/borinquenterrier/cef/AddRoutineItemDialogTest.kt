package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AddRoutineItemDialogTest {

    @Test
    fun testDialogRendersWithDefaultFields() = runComposeUiTest {
        setContent { AddRoutineItemDialog(onDismiss = {}, onSave = {}) }

        onNodeWithText("Add Routine Item").assertExists()
        onNodeWithText("Title").assertExists()
        onNodeWithText("Repeats on:").assertExists()
        onNodeWithText("Save").assertExists()
        onNodeWithText("Cancel").assertExists()

        // Day chips for every day of the week are rendered
        DayOfWeek.entries.forEach { day ->
            onNodeWithText(day.name.take(3)).assertExists()
        }
    }

    @Test
    fun testCancelButtonInvokesOnDismiss() = runComposeUiTest {
        var dismissed = false

        setContent { AddRoutineItemDialog(onDismiss = { dismissed = true }, onSave = {}) }

        onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun testTypingTitleAndTogglingDaysProducesCorrectEventOnSave() = runComposeUiTest {
        var savedEvent: TimeEvent? = null

        setContent { AddRoutineItemDialog(onDismiss = {}, onSave = { savedEvent = it }) }

        onNode(hasSetTextAction()).performTextInput("Morning Run")

        // Select Monday and Wednesday, then deselect Monday to exercise both toggle branches
        onNodeWithText("MON").performClick()
        onNodeWithText("WED").performClick()
        onNodeWithText("MON").performClick()

        onNodeWithText("Save").performClick()

        val event = assertNotNull(savedEvent)
        assertEquals("Morning Run", event.title)
        assertEquals(EventSource.ROUTINE, event.source)
        assertEquals(listOf(DayOfWeek.WEDNESDAY), event.recurrence?.daysOfWeek)
    }

    @Test
    fun testStartTimeFieldOpensPickerAndConfirmingClosesIt() = runComposeUiTest {
        setContent { AddRoutineItemDialog(onDismiss = {}, onSave = {}) }

        onNodeWithText("Start Time").performClick()
        onNodeWithText("Select Start Time").assertExists()

        onNodeWithText("OK").performClick()
        onNodeWithText("Select Start Time").assertDoesNotExist()
    }

    @Test
    fun testEndTimeFieldOpensPickerWithEndTitleAndCanBeCancelled() = runComposeUiTest {
        setContent { AddRoutineItemDialog(onDismiss = {}, onSave = {}) }

        onNodeWithText("End Time").performClick()
        onNodeWithText("Select End Time").assertExists()

        onAllNodesWithText("Cancel")[1].performClick()
        onNodeWithText("Select End Time").assertDoesNotExist()
    }

    @Test
    fun testStartDateFieldOpensDatePickerAndConfirmingClosesIt() = runComposeUiTest {
        setContent { AddRoutineItemDialog(onDismiss = {}, onSave = {}) }

        onNodeWithText("Start Date").performClick()
        onNodeWithText("OK").assertExists()

        onNodeWithText("OK").performClick()
        onNodeWithText("Start Date").assertExists()
    }

    @Test
    fun testEndDateFieldOpensDatePickerAndCanBeCancelled() = runComposeUiTest {
        setContent { AddRoutineItemDialog(onDismiss = {}, onSave = {}) }

        onNodeWithText("End Date").performClick()
        onNodeWithText("OK").assertExists()

        onAllNodesWithText("Cancel")[1].performClick()
        onNodeWithText("End Date").assertExists()
    }
}
