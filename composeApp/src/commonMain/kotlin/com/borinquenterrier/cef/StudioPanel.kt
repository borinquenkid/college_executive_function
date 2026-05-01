package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            Button(onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth()) {
                Text("Generate Summary")
            }
            Button(onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth()) {
                Text("Create Outline")
            }
            Button(onClick = { /*TODO*/ }, modifier = Modifier.fillMaxWidth()) {
                Text("Generate Q&A")
            }
            
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

            if (lastGeneratedEvents.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (!isConnected) {
                                // Handled in flow's message but we can add a check
                                return@launch
                            }
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
        } else {
            Text("Select a source to get started.")
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(statusMessage)
            }
        }
    }
}
