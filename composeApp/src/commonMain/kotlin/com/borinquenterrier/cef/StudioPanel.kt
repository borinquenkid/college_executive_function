package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun StudioPanel(
    modifier: Modifier = Modifier,
    selectedSource: SourceItem?,
    calendarAgent: CalendarAgent,
    container: DependencyContainer,
    onEventsGenerated: (List<Event>) -> Unit
) {
    val appController = container.appController
    val eventAgent = container.eventAgent

    val isLoading by eventAgent.isLoading.collectAsState()
    val statusMessage by eventAgent.statusMessage.collectAsState()
    val lastGeneratedEvents by eventAgent.lastGeneratedEvents.collectAsState()
    val persistedWarnings by eventAgent.persistedWarnings.collectAsState()
    val extractionWarning by eventAgent.extractionWarning.collectAsState()
    val isConnected by container.tokenRepository.isLinked.collectAsState()
    val pendingRequests by eventAgent.pendingRequestCount.collectAsState()
    val unresolvedConflicts by eventAgent.unresolvedConflicts.collectAsState()

    val displayStatus = StudioStatusFormatter.format(
        statusMessage, isLoading, pendingRequests, eventAgent.estimatedRemainingSeconds()
    )

    LaunchedEffect(Unit) {
        eventAgent.loadPersistedWarnings()
    }

    var eventsList by remember { mutableStateOf(emptyList<Event>()) }
    LaunchedEffect(selectedSource, lastGeneratedEvents) {
        eventsList = calendarAgent.getEvents("default")
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val deadlineSummary = remember(eventsList, today) { DeadlineSummary.from(eventsList, today) }
    val allWarnings = remember(lastGeneratedEvents, persistedWarnings, extractionWarning) {
        WarningAggregator.collect(lastGeneratedEvents, persistedWarnings, extractionWarning, today)
    }

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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .testTag("studio_panel"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Studio",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("semester_health_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Semester Health",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Next 7 Days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${deadlineSummary.dueIn7Days} deliverables",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("deliverables_7_days")
                        )
                    }
                    Column {
                        Text(
                            text = "Next 30 Days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${deadlineSummary.dueIn30Days} deliverables",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("deliverables_30_days")
                        )
                    }
                }
            }
        }

        if (selectedSource != null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            appController.launchInScope {
                                eventAgent.generateStudyPlan(selectedSource)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                            .testTag("process_syllabus_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(0.dp),
                        enabled = !isLoading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Process Syllabus & Plan Study",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Source Notes are shown whenever there are warnings — including the case where
                // extraction completed but found zero events (extractionWarning is non-null).
                if (allWarnings.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                                .testTag("source_discrepancies_card")
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Source Notes",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                SelectionContainer {
                                    Column {
                                        allWarnings.forEach { warning ->
                                            Text(
                                                "- $warning",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (lastGeneratedEvents.isNotEmpty()) {
                    val pushVariant = PushButtonState.variant(
                        hasConflicts = unresolvedConflicts.isNotEmpty(),
                        isConnected = isConnected
                    )

                    item {
                        Button(
                            onClick = {
                                appController.launchInScope {
                                    eventAgent.pushToCalendar()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("push_calendar_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (pushVariant) {
                                    PushButtonVariant.CONFLICT -> MaterialTheme.colorScheme.error
                                    PushButtonVariant.GOOGLE   -> MaterialTheme.colorScheme.tertiary
                                    PushButtonVariant.LOCAL    -> MaterialTheme.colorScheme.secondary
                                }
                            ),
                            enabled = !isLoading
                        ) {
                            Text(PushButtonState.label(pushVariant))
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                appController.launchInScope {
                                    try {
                                        val icsContent = generateIcsString(lastGeneratedEvents)
                                        val filePath = writeIcsFile(icsContent)
                                        eventAgent.updateStatus("Exported study plan: $filePath")
                                    } catch (e: Exception) {
                                        eventAgent.updateStatus("Export failed: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .testTag("export_ics_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            enabled = !isLoading
                        ) {
                            Text("Export Study Plan to .ics")
                        }
                    }

                    if (pushVariant == PushButtonVariant.CONFLICT) {
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
                            SelectionContainer {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        event.date.toString(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                "Select a source to get started.",
                modifier = Modifier.testTag("no_source_placeholder")
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).testTag("studio_loading_indicator"),
                        strokeWidth = 2.dp
                    )
                    Text(
                        displayStatus,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("status_message_text")
                    )
                }
            } else {
                Text(
                    displayStatus,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("status_message_text")
                )
            }
        }
    }
}
