package com.borinquenterrier.cef

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun GoogleLinkPrompt(
    onLink: suspend () -> Boolean,
    onLinked: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.CloudCircle, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Sync with Google Calendar", style = MaterialTheme.typography.titleMedium)
            }
            Text("Link your account to import syllabi from Drive and push events to your Google Calendar.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    if (onLink()) {
                        onLinked()
                    }
                }
            }) {
                Text("Link Google Account")
            }
        }
    }
}

@Composable
fun AcademicCalendarHeader(
    isGoogleLinked: Boolean,
    isSyncing: Boolean,
    onNavigateRoutine: () -> Unit,
    onNavigateHome: () -> Unit,
    onSync: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Button(
            onClick = onNavigateRoutine,
            modifier = Modifier.weight(1f)
        ) {
            Text("Weekly Routine")
        }

        Button(
            onClick = onNavigateHome,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Add Source")
        }

        if (isGoogleLinked) {
            IconButton(
                onClick = onSync,
                enabled = !isSyncing
            ) {
                if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.Sync, contentDescription = "Sync Now")
            }
        }
    }
}

@Composable
fun EventListContent(
    groupedEvents: Map<LocalDate, List<Event>>,
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
                    onBreakItDown = if (isBreakable) { { onEventSelected(event) } } else null
                )
            }
        }
    }
}
