@file:UiOnly
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun ChatPanel(
    modifier: Modifier = Modifier,
    appController: AppController,
    onOpenConversations: () -> Unit = {}
) {
    val selectedSource by appController.selectedSource.asStateFlow().collectAsState()
    val messages by appController.chatMessages.asStateFlow().collectAsState()
    val currentConversationId by appController.currentConversationId.asStateFlow().collectAsState()
    val conversations by appController.conversations.asStateFlow().collectAsState()
    val currentConversation = conversations.firstOrNull { it.id == currentConversationId }
    val currentTitle = currentConversation?.title ?: Conversation.DEFAULT_CONVERSATION_TITLE
    // Scope now comes from the conversation's persisted pin, not throw-away local state:
    // "All" (or an unknown/legacy scope) reasons across every source; a single-source pin does not.
    val useAllSources = currentConversation?.sourceScope !is ChatSourceScope.Source
    val sourceItems by appController.sourceItems.asStateFlow().collectAsState()
    val contextAgent = appController.container.contextAgent
    val eventAgent = appController.container.eventAgent
    val persistedWarnings by eventAgent.persistedWarnings.collectAsState()
    val liveWarnings by eventAgent.lastGeneratedEvents.collectAsState()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val ingestionWarnings = remember(persistedWarnings, liveWarnings) {
        WarningAggregator.collect(liveWarnings, persistedWarnings, null, today)
    }

    LaunchedEffect(Unit) { eventAgent.loadPersistedWarnings() }

    var newMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        // ── Top bar: conversation controls ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenConversations,
                modifier = Modifier.testTag("conversations_open_button")
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Conversations")
            }
            Text(
                currentTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = { appController.newConversation() },
                modifier = Modifier.testTag("new_conversation_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New chat")
            }
        }

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
                onClick = {
                    appController.setConversationSourceScope(currentConversationId, ChatSourceScope.All)
                },
                label = {
                    Text(
                        "All Sources (${sourceItems.size})",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.height(32.dp)
            )
            selectedSource?.let { source ->
                val chipLabel = ChatInputPresenter.chipLabel(source.title)
                FilterChip(
                    selected = !useAllSources,
                    onClick = {
                        appController.setConversationSourceScope(
                            currentConversationId,
                            ChatSourceScope.Source(source.title)
                        )
                    },
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
                    appController.addChatMessage(
                        ChatMessage.create(
                            userText,
                            ChatRole.USER,
                            Clock.System.now().toEpochMilliseconds(),
                            currentConversationId
                        )
                    )
                    newMessage = ""

                    coroutineScope.launch {
                        val aiResponse = when {
                            useAllSources -> contextAgent.queryAllSources(
                                sources = sourceItems,
                                conversationHistory = history,
                                question = userText,
                                warnings = ingestionWarnings
                            )

                            selectedSource != null -> contextAgent.querySource(
                                selectedSource!!,
                                userText
                            )

                            else -> "Please select a source from the Sources panel, or switch to All Sources mode."
                        }
                        appController.addChatMessage(
                            ChatMessage.create(
                                aiResponse,
                                ChatRole.AI,
                                Clock.System.now().toEpochMilliseconds(),
                                currentConversationId
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                val placeholder = ChatInputPresenter.placeholder(useAllSources, sourceItems.size, selectedSource?.title)
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
    val isUser = message.role == ChatRole.USER
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
                    message.displayLabel,
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
