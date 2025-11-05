package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioPanel(modifier: Modifier = Modifier, selectedSource: SourceItem?, onEventsGenerated: (List<CalendarEvent>) -> Unit) {
    var isLoading by remember { mutableStateOf(false) }
    var generatedContent by remember { mutableStateOf("Select a source and an action.") }
    val coroutineScope = rememberCoroutineScope()
    val aiService = rememberAIService()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedSource != null) {
            when (selectedSource.title) {
                "Syllabus" -> {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                val events = aiService.generateCalendarEvents("Analyze the syllabus ${selectedSource.content} for dates and deliverables.")
                                onEventsGenerated(events)
                                generatedContent = "${events.size} events added to your calendar."
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Analyze Syllabus for Dates")
                    }
                }
                "Calendar" -> {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                val events = aiService.generateCalendarEvents("Add the events from ${selectedSource.content} to the Academic Calendar.")
                                onEventsGenerated(events)
                                generatedContent = "${events.size} events added to your calendar."
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Events to Academic Calendar")
                    }
                }
            }
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
