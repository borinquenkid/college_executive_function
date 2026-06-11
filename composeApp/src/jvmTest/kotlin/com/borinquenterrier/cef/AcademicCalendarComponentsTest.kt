package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class AcademicCalendarComponentsTest {

    // --- AcademicCalendarHeader ---

    @Test
    fun `header shows sync button when Google is linked`() = runComposeUiTest {
        setContent {
            AcademicCalendarHeader(
                isGoogleLinked = true,
                isSyncing = false,
                onNavigateRoutine = {},
                onNavigateHome = {},
                onSync = {}
            )
        }
        onNodeWithText("Weekly Routine").assertExists()
        onNodeWithText("Add Source").assertExists()
        onNodeWithContentDescription("Sync Now").assertExists()
    }

    @Test
    fun `header hides sync button when Google is not linked`() = runComposeUiTest {
        setContent {
            AcademicCalendarHeader(
                isGoogleLinked = false,
                isSyncing = false,
                onNavigateRoutine = {},
                onNavigateHome = {},
                onSync = {}
            )
        }
        onNodeWithText("Weekly Routine").assertExists()
        onNodeWithText("Add Source").assertExists()
        onNodeWithContentDescription("Sync Now").assertDoesNotExist()
    }

    @Test
    fun `header fires onNavigateRoutine when Weekly Routine clicked`() = runComposeUiTest {
        var called = false
        setContent {
            AcademicCalendarHeader(
                isGoogleLinked = false,
                isSyncing = false,
                onNavigateRoutine = { called = true },
                onNavigateHome = {},
                onSync = {}
            )
        }
        onNodeWithText("Weekly Routine").performClick()
        assertEquals(true, called)
    }

    @Test
    fun `header fires onNavigateHome when Add Source clicked`() = runComposeUiTest {
        var called = false
        setContent {
            AcademicCalendarHeader(
                isGoogleLinked = false,
                isSyncing = false,
                onNavigateRoutine = {},
                onNavigateHome = { called = true },
                onSync = {}
            )
        }
        onNodeWithText("Add Source").performClick()
        assertEquals(true, called)
    }

    @Test
    fun `header fires onSync when sync button clicked`() = runComposeUiTest {
        var called = false
        setContent {
            AcademicCalendarHeader(
                isGoogleLinked = true,
                isSyncing = false,
                onNavigateRoutine = {},
                onNavigateHome = {},
                onSync = { called = true }
            )
        }
        onNodeWithContentDescription("Sync Now").performClick()
        assertEquals(true, called)
    }

    // --- EventListContent ---

    @Test
    fun `EventListContent shows empty state message when no events`() = runComposeUiTest {
        setContent {
            EventListContent(groupedEvents = emptyMap(), onEventSelected = {})
        }
        onNodeWithText(
            "No events yet. Add a syllabus, calendar source, or weekly routine to get started."
        ).assertExists()
    }

    @Test
    fun `EventListContent shows event title when events exist`() = runComposeUiTest {
        val date = LocalDate(2027, 1, 15)
        val event = DayEvent(
            id = "e1",
            title = "Math Homework",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = date
        )
        setContent {
            EventListContent(
                groupedEvents = mapOf(date to listOf(event)),
                onEventSelected = {}
            )
        }
        onNodeWithText("Math Homework").assertExists()
    }

    @Test
    fun `EventListContent fires onEventSelected via Break It Down for decomposable event`() =
        runComposeUiTest {
            val date = LocalDate(2027, 1, 15)
            val deadlineEvent = DayEvent(
                id = "e1",
                title = "Term Paper",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = date
            )
            var selected: Event? = null
            setContent {
                EventListContent(
                    groupedEvents = mapOf(date to listOf(deadlineEvent)),
                    onEventSelected = { selected = it }
                )
            }
            onNodeWithText("Break It Down (AI)").performClick()
            assertNotNull(selected)
            assertEquals("e1", selected?.id)
        }

    @Test
    fun `EventListContent non-decomposable event has no Break It Down button`() =
        runComposeUiTest {
            val date = LocalDate(2027, 1, 15)
            val routineEvent = DayEvent(
                id = "r1",
                title = "Morning Jog",
                source = EventSource.ROUTINE,
                category = AcademicCategory.REGULAR,
                date = date
            )
            setContent {
                EventListContent(
                    groupedEvents = mapOf(date to listOf(routineEvent)),
                    onEventSelected = {}
                )
            }
            onNodeWithText("Break It Down (AI)").assertDoesNotExist()
        }
}
