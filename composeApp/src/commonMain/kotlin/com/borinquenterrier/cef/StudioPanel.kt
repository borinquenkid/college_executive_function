package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

@Composable
fun StudioPanel(modifier: Modifier = Modifier, selectedSource: SourceItem?, onEventsGenerated: (List<Event>) -> Unit) {
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
            // Example of how to use the AI service with the new model
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
                Text("Analyze for Dates (TEMP)")
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
