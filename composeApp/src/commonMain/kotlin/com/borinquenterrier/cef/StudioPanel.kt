package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch

@Composable
fun StudioPanel(
    modifier: Modifier = Modifier, 
    selectedSource: SourceItem?, 
    onEventsGenerated: (List<Event>) -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var generatedContent by remember { mutableStateOf("Select a source and an action.") }
    val coroutineScope = rememberCoroutineScope()
    val aiService = rememberAIService()
    
    val settings = rememberSettings()
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val syncService = remember { GoogleCalendarSyncService(HttpClient { 
        install(ContentNegotiation) { json() } 
    }) }
    
    // Track the last generated events locally to push them
    var lastGeneratedEvents by remember { mutableStateOf<List<Event>>(emptyList()) }

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
                        isLoading = true
                        val events = aiService.generateCalendarEvents("Analyze the syllabus ${selectedSource.content} for dates and deliverables.")
                        onEventsGenerated(events)
                        lastGeneratedEvents = events
                        generatedContent = "${events.size} events added to your internal calendar."
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Extract Deliverables (AI)")
            }

            if (lastGeneratedEvents.isNotEmpty() && tokenRepository.hasTokens()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            val token = tokenRepository.getAccessToken()!!
                            // In a real app, we'd map our CEF Events back to iCal4j VEvents first
                            // For this MVP, we'll just demonstrate pushing one or informing completion
                            generatedContent = "Pushing ${lastGeneratedEvents.size} events to Google..."
                            
                            var successCount = 0
                            lastGeneratedEvents.forEach { event ->
                                try {
                                    syncService.syncEvent(event, token)
                                    successCount++
                                } catch (e: Exception) {
                                    // Log or handle individual failures
                                }
                            }
                            
                            generatedContent = "Success! $successCount events pushed to your Google Calendar."
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Push to Google Calendar")
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
                Text(generatedContent)
            }
        }
    }
}
