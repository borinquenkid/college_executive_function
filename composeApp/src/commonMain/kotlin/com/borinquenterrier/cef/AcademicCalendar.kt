package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicCalendar(modifier: Modifier = Modifier, events: List<CalendarEvent>, onNavigate: (Screen) -> Unit) {
    val settings = rememberSettings()
    val repository = remember { RoutineRepository(settings) }
    var routineItems by remember { mutableStateOf(emptyList<RoutineItem>()) }

    // Load the routine items
    LaunchedEffect(repository) {
        routineItems = repository.getRoutine()
    }

    // Generate calendar events from routine items for the next 7 days
    val generatedRoutineEvents = remember(routineItems) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val weekDates = (0..6).map { today.plus(it, DateTimeUnit.DAY) }

        routineItems.flatMap { item ->
            weekDates
                .filter { date ->
                    date.dayOfWeek == item.dayOfWeek && date >= item.startDate && date <= item.endDate
                }
                .map { date ->
                    CalendarEvent(
                        title = "[Routine] ${item.title}",
                        startTime = LocalDateTime(date, item.startTime).toInstant(TimeZone.currentSystemDefault()),
                        endTime = LocalDateTime(date, item.endTime).toInstant(TimeZone.currentSystemDefault())
                    )
                }
        }
    }

    val allEvents = (events + generatedRoutineEvents).sortedBy { it.startTime }
    val groupedEvents = allEvents.groupBy { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date }

    Column(modifier = modifier) {
        Button(
            onClick = { onNavigate(Screen.Routine) },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("Manage Weekly Routine")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (groupedEvents.isEmpty()) {
                item {
                    Text(
                        text = "No events yet. Add a syllabus, calendar source, or weekly routine to get started.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                groupedEvents.forEach { (date, eventsOnDate) ->
                    stickyHeader {
                        Text(
                            text = date.toString(),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(eventsOnDate) { event ->
                        Text(
                            text = event.title,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
