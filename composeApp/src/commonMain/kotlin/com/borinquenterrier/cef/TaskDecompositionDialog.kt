package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TaskDecompositionDialog(event: Event, eventAgent: EventAgent, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val decomposedTasks by eventAgent.decomposedTasks.collectAsState()
    val isLoading by eventAgent.isLoading.collectAsState()
    val statusMessage by eventAgent.statusMessage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Break It Down", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Due: ${event.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (decomposedTasks.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    if (isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
                        }
                    } else {
                        Button(
                            onClick = { scope.launch { eventAgent.decomposeTask(event) } },
                            modifier = Modifier.fillMaxWidth().testTag("break_it_down_button")
                        ) {
                            Text("Break It Down (AI)")
                        }
                    }
                } else {
                    HorizontalDivider()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 160.dp)
                    ) {
                        items(decomposedTasks) { task ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        "${task.daysBeforeDue} day${if (task.daysBeforeDue != 1) "s" else ""} before due",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(task.title, style = MaterialTheme.typography.bodyMedium)
                                    if (task.description.isNotBlank()) {
                                        Text(
                                            task.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = { scope.launch { eventAgent.acceptDecomposition(); onDismiss() } },
                        modifier = Modifier.fillMaxWidth().testTag("add_steps_button"),
                        enabled = !isLoading
                    ) {
                        Text("Add Steps to Calendar")
                    }
                }

                if (statusMessage.isNotBlank() && statusMessage != "Select a source and an action.") {
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
