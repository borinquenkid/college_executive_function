package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicCalendar(modifier: Modifier = Modifier, aiGeneratedEvents: List<Event>, onNavigate: (Screen) -> Unit) {
    val settings = rememberSettings()
    val repository = remember { RoutineRepository(settings) }
    var routineEvents by remember { mutableStateOf(emptyList<TimeEvent>()) }

    // Load the routine items
    LaunchedEffect(repository) {
        routineEvents = repository.getRoutineEvents()
    }

    // Expand recurring events for a two-week view (last week + this week)
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val viewStartDate = today.minus(7, DateTimeUnit.DAY)
    val viewEndDate = today.plus(7, DateTimeUnit.DAY)

    val allExpandedEvents = remember(aiGeneratedEvents, routineEvents) {
        val expandedRoutineEvents = EventGenerator.expandEvents(routineEvents, viewStartDate, viewEndDate)
        val expandedAiEvents = EventGenerator.expandEvents(aiGeneratedEvents, viewStartDate, viewEndDate)
        (expandedRoutineEvents + expandedAiEvents).sortedBy { event ->
            when (event) {
                is TimeEvent -> event.date.atStartOfDayIn(TimeZone.currentSystemDefault())
                is DayEvent -> event.date.atStartOfDayIn(TimeZone.currentSystemDefault())
            }
        }
    }

    val groupedEvents = allExpandedEvents.groupBy { event ->
        when (event) {
            is TimeEvent -> event.date
            is DayEvent -> event.date
        }
    }

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
                        EventItemView(event)
                    }
                }
            }
        }
    }
}

@Composable
fun EventItemView(event: Event) {
    val borderColor = when (event.source) {
        EventSource.ROUTINE -> Color(0xFF4CAF50) // Green
        EventSource.AI_GENERATED -> Color(0xFF2196F3) // Blue
        EventSource.MANUAL -> Color(0xFFFFC107) // Amber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(2.dp, borderColor, CardDefaults.shape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (event) {
                is TimeEvent -> {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text("From ${event.startTime} to ${event.endTime}")
                }
                is DayEvent -> {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text("All day")
                }
            }
        }
    }
}
