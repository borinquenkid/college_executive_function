package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicCalendar(modifier: Modifier = Modifier, events: List<CalendarEvent>) {
    val groupedEvents = events.groupBy { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (groupedEvents.isEmpty()) {
            item {
                Text(
                    text = "No events yet. Events will be added here once you add a syllabus or other calendar source.",
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
