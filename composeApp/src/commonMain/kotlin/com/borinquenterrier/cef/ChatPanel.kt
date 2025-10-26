package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChatMessage(val author: String, val content: String)

@Composable
fun ChatPanel(modifier: Modifier = Modifier) {
    val messages = remember {
        mutableStateOf(listOf(ChatMessage("AI", "Hello! How can I help you today?")))
    }
    var newMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages.value) { message ->
                MessageView(message)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = newMessage,
                onValueChange = { newMessage = it },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (newMessage.isNotBlank()) {
                    messages.value = messages.value + ChatMessage("User", newMessage)
                    newMessage = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun MessageView(message: ChatMessage) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(message.author, style = MaterialTheme.typography.titleSmall)
        Text(message.content, style = MaterialTheme.typography.bodyMedium)
    }
}
