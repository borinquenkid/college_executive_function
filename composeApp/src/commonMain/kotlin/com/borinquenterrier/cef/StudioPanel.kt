package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
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
fun StudioPanel(modifier: Modifier = Modifier, selectedSource: SourceItem?) {
    var isLoading by remember { mutableStateOf(false) }
    var generatedContent by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val aiService = rememberAIService()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    val prompt = if (selectedSource != null) {
                        "Generate a general overview of ${selectedSource.title}"
                    } else {
                        "Generate a general overview of the content"
                    }
                    generatedContent = aiService.generateResponse(prompt)
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Content")
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { 
                coroutineScope.launch {
                    isLoading = true
                    val prompt = if (selectedSource != null) {
                        "Summarize ${selectedSource.title}"
                    } else {
                        "Summarize the content"
                    }
                    generatedContent = aiService.generateResponse(prompt)
                    isLoading = false
                }
            }, label = { Text("Summarize") })
            AssistChip(onClick = { 
                coroutineScope.launch {
                    isLoading = true
                    val prompt = if (selectedSource != null) {
                        "Create an outline of ${selectedSource.title}"
                    } else {
                        "Create an outline of the content"
                    }
                    generatedContent = aiService.generateResponse(prompt)
                    isLoading = false
                }
            }, label = { Text("Outline") })
            AssistChip(onClick = { 
                coroutineScope.launch {
                    isLoading = true
                    val prompt = if (selectedSource != null) {
                        "Generate Q&A based on ${selectedSource.title}"
                    } else {
                        "Generate Q&A based on the content"
                    }
                    generatedContent = aiService.generateResponse(prompt)
                    isLoading = false
                }
            }, label = { Text("Q&A") })
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
