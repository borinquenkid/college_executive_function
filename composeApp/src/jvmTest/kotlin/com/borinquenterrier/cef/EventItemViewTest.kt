package com.borinquenterrier.cef

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EventItemViewTest {

    @Test
    fun `DEADLINE event shows deadline chip and study progress`() = runComposeUiTest {
        val event = DayEvent(
            id = "d1",
            title = "Essay Due",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2027, 6, 20)
        )
        setContent { EventItemView(event = event, onBreakItDown = null) }
        onNodeWithText("Essay Due").assertExists()
        onNodeWithText("Study Progress").assertExists()
    }

    @Test
    fun `FINALS event shows deadline chip and study progress`() = runComposeUiTest {
        val event = DayEvent(
            id = "f1",
            title = "Math Final",
            source = EventSource.MANUAL,
            category = AcademicCategory.FINALS,
            date = LocalDate(2027, 6, 20)
        )
        setContent { EventItemView(event = event, onBreakItDown = null) }
        onNodeWithText("Math Final").assertExists()
        onNodeWithText("Study Progress").assertExists()
    }

    @Test
    fun `REGULAR category does not show chip or progress`() = runComposeUiTest {
        val event = DayEvent(
            id = "r1",
            title = "Class Meeting",
            source = EventSource.ROUTINE,
            category = AcademicCategory.REGULAR,
            date = LocalDate(2027, 6, 20)
        )
        setContent { EventItemView(event = event, onBreakItDown = null) }
        onNodeWithText("Class Meeting").assertExists()
        onNodeWithText("Study Progress").assertDoesNotExist()
    }

    @Test
    fun `Break It Down button shows and fires callback when provided`() = runComposeUiTest {
        val event = DayEvent(
            id = "d2",
            title = "Research Paper",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2027, 6, 20)
        )
        var clickCount = 0
        setContent { EventItemView(event = event, onBreakItDown = { clickCount++ }) }
        val btn = onNodeWithText("Break It Down (AI)")
        btn.assertExists()
        btn.performClick()
        assertEquals(1, clickCount)
    }

    @Test
    fun `Break It Down button hidden when callback is null`() = runComposeUiTest {
        val event = DayEvent(
            id = "d3",
            title = "Research Paper",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2027, 6, 20)
        )
        setContent { EventItemView(event = event, onBreakItDown = null) }
        onNodeWithText("Break It Down (AI)").assertDoesNotExist()
    }

    @Test
    fun `DayEvent shows All day text`() = runComposeUiTest {
        val event = DayEvent(
            id = "day1",
            title = "Spring Break",
            source = EventSource.MANUAL,
            category = AcademicCategory.HOLIDAY,
            date = LocalDate(2027, 6, 20)
        )
        setContent { EventItemView(event = event, onBreakItDown = null) }
        onNodeWithText("Spring Break").assertExists()
        onNodeWithText("All day").assertExists()
    }

    @Test
    fun `TimeEvent shows time range and not All day`() = runComposeUiTest {
        val event = TimeEvent(
            id = "t1",
            title = "CS 101 Lecture",
            source = EventSource.ROUTINE,
            category = AcademicCategory.CLASS,
            date = LocalDate(2027, 6, 20),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 30)
        )
        setContent { EventItemView(event = event, onBreakItDown = null) }
        onNodeWithText("CS 101 Lecture").assertExists()
        onNodeWithText("All day").assertDoesNotExist()
    }
}
