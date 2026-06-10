package com.borinquenterrier.cef

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
    val sourceItems by appController.sourceItems.collectAsState()
    val contextAgent = appController.container.contextAgent

    var newMessage by remember { mutableStateOf("") }
    // true  = reason across every loaded source (multi-source mode)
    // false = scope to the currently selected source only
    var useAllSources by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        // ── Scope selector ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = useAllSources,
                onClick = { useAllSources = true },
                label = {
                    Text(
                        "All Sources (${sourceItems.size})",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.height(32.dp)
            )
            if (selectedSource != null) {
                val chipLabel = selectedSource!!.title.let {
                    if (it.length > 22) it.take(22) + "\u2026" else it
                }
                FilterChip(
                    selected = !useAllSources,
                    onClick = { useAllSources = false },
                    label = {
                        Text(chipLabel, style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        // ── Message list ──────────────────────────────────────────────────
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { message ->
                MessageView(message)
            }
        }

        // ── Input row ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sendMessage = {
                if (newMessage.isNotBlank()) {
                    val userText = newMessage
                    // Snapshot history BEFORE appending the new user message
                    val history = messages
                    appController.addChatMessage(ChatMessage("User", userText))
                    newMessage = ""

                    coroutineScope.launch {
                        val aiResponse = when {
                            useAllSources -> contextAgent.queryAllSources(
                                sources = sourceItems,
                                conversationHistory = history,
                                question = userText
                            )

                            selectedSource != null -> contextAgent.querySource(
                                selectedSource!!,
                                userText
                            )

                            else -> "Please select a source from the Sources panel, or switch to All Sources mode."
                        }
                        appController.addChatMessage(ChatMessage("AI", aiResponse))
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                val placeholder = when {
                    useAllSources && sourceItems.isEmpty() -> "Add sources to get started\u2026"
                    useAllSources -> "Ask anything across all ${sourceItems.size} source(s)\u2026"
                    selectedSource != null -> "Ask about ${selectedSource!!.title.take(20)}\u2026"
                    else -> "Select a source, or switch to All Sources mode\u2026"
                }
                if (newMessage.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_input_field")
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                sendMessage()
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true
                )
            }
            IconButton(
                onClick = sendMessage,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun MessageView(message: ChatMessage) {
    val isUser = message.author == "User"
    val horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    val backgroundColor =
        if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isUser) {
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                modifier = Modifier.padding(end = 4.dp).size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        SelectionContainer {
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

        if (!isUser) {
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                modifier = Modifier.padding(start = 4.dp).size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
