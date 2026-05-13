package com.borinquenterrier.cef

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class ChatMessage(val author: String, val content: String)

@Composable
fun ChatPanel(
    modifier: Modifier = Modifier, 
    appController: AppController
) {
    val selectedSource by appController.selectedSource.collectAsState()
    val messages by appController.chatMessages.collectAsState()
    val contextAgent = appController.container.contextAgent
    
    var newMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { message ->
                MessageView(message)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (newMessage.isEmpty()) {
                    Text(
                        "Ask about syllabus...", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true
                )
            }
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        if (newMessage.isNotBlank()) {
                            val userText = newMessage
                            appController.addChatMessage(ChatMessage("User", userText))
                            newMessage = ""

                            val aiResponse = if (selectedSource != null) {
                                contextAgent.querySource(selectedSource!!, userText)
                            } else {
                                // Fallback for general chat
                                "Please select a source (like a syllabus) so I can answer your questions accurately."
                            }
                            appController.addChatMessage(ChatMessage("AI", aiResponse))
                        }
                    }
                },
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun MessageView(message: ChatMessage) {
    val isUser = message.author == "User"
    val horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = horizontalArrangement
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                message.author, 
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                message.content, 
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}
