package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
    unifiedRepository: UnifiedCalendarRepository,
    onEventsGenerated: (List<Event>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val aiService = rememberAIService()
    val logger = rememberLogger()
    
    val driverFactory = rememberDriverFactory()
    val database = remember(driverFactory) { AppDatabase(driverFactory.createDriver()) }
    
    val studioFlow = remember(aiService, unifiedRepository, database) {
        StudioFlow(aiService, unifiedRepository, database, logger = logger)
    }

    val isLoading by studioFlow.isLoading.collectAsState()
    val statusMessage by studioFlow.statusMessage.collectAsState()
    val lastGeneratedEvents by studioFlow.lastGeneratedEvents.collectAsState()

    val settings = rememberSettings()
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val isConnected = tokenRepository.hasTokens()

    // Push events back to parent when they are generated in the flow
    LaunchedEffect(lastGeneratedEvents) {
        if (lastGeneratedEvents.isNotEmpty()) {
            onEventsGenerated(lastGeneratedEvents)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Studio", style = MaterialTheme.typography.headlineSmall)

        if (selectedSource != null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                studioFlow.generateStudyPlan(selectedSource)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isLoading
                    ) {
                        Text("Generate Study Plan (AI)")
                    }
                }

                item {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                studioFlow.extractDeliverables(selectedSource)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Extract Deliverables (AI)")
                    }
                }

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

                    item {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (!isConnected) return@launch
                                    studioFlow.pushToCalendar()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            enabled = !isLoading && isConnected
                        ) {
                            Text(if (isConnected) "Push to Google Calendar" else "Connect to Google to Push")
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
