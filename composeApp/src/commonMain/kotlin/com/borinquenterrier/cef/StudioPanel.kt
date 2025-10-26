package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudioPanel(modifier: Modifier = Modifier) {
    var isLoading by remember { mutableStateOf(false) }
    var generatedContent by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    delay(2000) // Simulate a network request
                    generatedContent = "This is some generated content."
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Content")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { /*TODO*/ }, label = { Text("Summarize") })
            AssistChip(onClick = { /*TODO*/ }, label = { Text("Outline") })
            AssistChip(onClick = { /*TODO*/ }, label = { Text("Q&A") })
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
