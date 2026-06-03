package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import com.borinquenterrier.cef.db.AppDatabase

@Composable
fun StudioPanel(
    modifier: Modifier = Modifier, 
    selectedSource: SourceItem?, 
    calendarAgent: CalendarAgent,
    container: DependencyContainer,
    onEventsGenerated: (List<Event>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val eventAgent = container.eventAgent

    val isLoading by eventAgent.isLoading.collectAsState()
    val statusMessage by eventAgent.statusMessage.collectAsState()
    val lastGeneratedEvents by eventAgent.lastGeneratedEvents.collectAsState()

    val isConnected by container.tokenRepository.isLinked.collectAsState()

    // Push events back to parent when they are generated in the flow
    LaunchedEffect(lastGeneratedEvents) {
        if (lastGeneratedEvents.isNotEmpty()) {
            onEventsGenerated(lastGeneratedEvents)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Studio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))

        if (selectedSource != null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                eventAgent.generateStudyPlan(selectedSource)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(0.dp),
                        enabled = !isLoading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Process Syllabus & Plan Study", style = MaterialTheme.typography.labelLarge)
                        }
                    }                }

                if (lastGeneratedEvents.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    val eventsWithWarnings = lastGeneratedEvents.filter { it.warning != null }
                    if (eventsWithWarnings.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Source Discrepancies Found", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    eventsWithWarnings.forEach { event ->
                                        Text("- ${event.title}: ${event.warning}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    val hasConflicts = lastGeneratedEvents.any { event -> 
                        // Logic to detect if this event was already rejected 
                        // (In this pass, if it's still in the list after a sync attempt, it's a conflict)
                        statusMessage.contains("conflicts need review")
                    }

                    item {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (!isConnected) return@launch
                                    eventAgent.pushToCalendar()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasConflicts) MaterialTheme.colorScheme.error else if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            enabled = !isLoading && isConnected
                        ) {
                            Text(
                                if (hasConflicts) "Force Sync Remaining" 
                                else if (isConnected) "Push to Google Calendar" 
                                else "Connect to Google to Push"
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val icsContent = generateIcsString(lastGeneratedEvents)
                                        val filePath = writeIcsFile(icsContent)
                                        eventAgent.updateStatus("Exported study plan: $filePath")
                                    } catch (e: Exception) {
                                        eventAgent.updateStatus("Export failed: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            enabled = !isLoading
                        ) {
                            Text("Export Study Plan to .ics")
                        }
                    }

                    if (hasConflicts) {
                        item {
                            Text(
                                "The items below overlap with your existing schedule. You can modify them or force them into the calendar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    
                    items(lastGeneratedEvents) { event ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                Text(event.date.toString(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            Text("Select a source to get started.")
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
