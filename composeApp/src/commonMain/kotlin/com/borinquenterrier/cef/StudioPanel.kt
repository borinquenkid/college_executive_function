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
        install(ContentNegotiation) { 
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }) 
        } 
    }) }
    val calendarRepository = remember(syncService, tokenRepository) { 
        GoogleRemoteCalendarRepository(syncService, tokenRepository) 
    }
    val programmaticExtractor = remember { KeywordEventExtractor() }
    
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
                        val rawEvents = aiService.generateCalendarEvents("Extract Academic Milestones from this calendar source: ${selectedSource.content}")
                        // Apply programmatic rules on top of AI/Raw results
                        val categorizedEvents = programmaticExtractor.extract(rawEvents)
                        
                        onEventsGenerated(categorizedEvents)
                        lastGeneratedEvents = categorizedEvents
                        generatedContent = "Academic milestones (Holidays, Deadlines, Finals) identified."
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Analyze Academic Calendar (AI)")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        val rawEvents = aiService.generateCalendarEvents("Analyze the syllabus ${selectedSource.content} for dates and deliverables.")
                        val categorizedEvents = programmaticExtractor.extract(rawEvents)
                        
                        onEventsGenerated(categorizedEvents)
                        lastGeneratedEvents = categorizedEvents
                        generatedContent = "${categorizedEvents.size} events added to your internal calendar."
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
                            generatedContent = "Checking for conflicts and pushing ${lastGeneratedEvents.size} events..."
                            
                            var successCount = 0
                            var conflictFound = false
                            
                            for (event in lastGeneratedEvents) {
                                try {
                                    // Use 'default' to target the CEF Academic calendar
                                    calendarRepository.saveEvent(event, "default")
                                    successCount++
                                } catch (e: OverlapException) {
                                    generatedContent = "Conflict: '${event.title}' overlaps with '${e.existingEvent.title}' on your calendar."
                                    conflictFound = true
                                    break
                                } catch (e: Exception) {
                                    generatedContent = "Error: ${e.message}"
                                    conflictFound = true
                                    break
                                }
                            }
                            
                            if (!conflictFound) {
                                generatedContent = "Success! $successCount events pushed to your CEF Academic Calendar."
                            }
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
