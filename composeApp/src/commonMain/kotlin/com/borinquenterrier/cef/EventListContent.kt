@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
fun EventListContent(
    groupedEvents: Map<LocalDate, List<Event>>,
    today: LocalDate,
    onEventSelected: (Event) -> Unit
) {
    if (groupedEvents.isEmpty()) {
        Text(
            text = "No events yet. Add a syllabus, calendar source, or weekly routine to get started.",
            modifier = Modifier.padding(16.dp)
        )
    } else {
        groupedEvents.forEach { (date, eventsOnDate) ->
            Text(
                text = date.toString(),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            eventsOnDate.forEach { event ->
                val isBreakable = CalendarEventGrouper.isDecomposable(event)
                EventItemView(
                    event = event,
                    today = today,
                    onBreakItDown = if (isBreakable) {
                        { onEventSelected(event) }
                    } else null
                )
            }
        }
    }
}
