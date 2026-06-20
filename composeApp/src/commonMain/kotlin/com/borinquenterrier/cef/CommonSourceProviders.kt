package com.borinquenterrier.cef

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.fillMaxWidth

class LocalFileSourceProvider(
    private val ingestionAgent: IngestionAgent,
    private val aiService: AIService,
    private val filePicker: @Composable (onFilesSelected: (List<String>) -> Unit) -> Unit = { onFilesSelected ->
        FilePicker(
            show = true,
            onFilesSelected = onFilesSelected
        )
    }
) : SourceProvider {
    override val id = "local_file"
    override val displayName = "File"
    override val icon = Icons.Default.Add

    override fun isAuthorized() = aiService.isConfigured()

    @Composable
    override fun SelectorUI(onSourceAdded: (SourceItem) -> Unit, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        val handler =
            remember(ingestionAgent, scope) { SourceIngestionHandler(ingestionAgent, scope) }
        var hasTriggered by remember { mutableStateOf(false) }
        var isIngesting by remember { mutableStateOf(false) }
        var ingestingTitle by remember { mutableStateOf("Reading Document") }

        if (isIngesting) {
            IngestingProgressDialog(
                title = ingestingTitle,
                message = "Extracting text and analyzing structure..."
            )
        }

        if (!hasTriggered) {
            filePicker { paths ->
                hasTriggered = true
                if (paths.isEmpty()) {
                    onDismiss()
                } else {
                    ingestingTitle = if (paths.size == 1) "Reading Document"
                                     else "Reading ${paths.size} Documents"
                    val pendingSources = mutableListOf<SourceItem>()
                    handler.ingestLocalFiles(
                        paths = paths,
                        onStart = { isIngesting = true },
                        onEachSuccess = { pendingSources.add(it) },
                        onFinish = {
                            isIngesting = false
                            pendingSources.forEach { onSourceAdded(it) }
                            if (pendingSources.isEmpty()) onDismiss()
                        }
                    )
                }
            }
        }
    }
}

class UrlSourceProvider(
    private val ingestionAgent: IngestionAgent,
    private val aiService: AIService
) : SourceProvider {
    override val id = "url"
    override val displayName = "URL"
    override val icon = Icons.Default.Link

    override fun isAuthorized() = aiService.isConfigured()

    @Composable
    override fun SelectorUI(onSourceAdded: (SourceItem) -> Unit, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        val handler =
            remember(ingestionAgent, scope) { SourceIngestionHandler(ingestionAgent, scope) }
        var url by remember { mutableStateOf("") }
        var isIngesting by remember { mutableStateOf(false) }

        val submitUrl = {
            if (url.isNotBlank()) {
                handler.ingestUrl(
                    url = url,
                    onStart = { isIngesting = true },
                    onSuccess = onSourceAdded,
                    onFailure = onDismiss,
                    onFinish = { isIngesting = false }
                )
            }
        }

        if (isIngesting) {
            IngestingProgressDialog(
                title = "Reading URL",
                message = "Fetching content and analyzing structure..."
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Add Source from URL") },
                text = {
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                    submitUrl()
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                },
                confirmButton = {
                    TextButton(onClick = submitUrl) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

class GoogleDriveSourceProvider(
    private val ingestionAgent: IngestionAgent,
    private val driveService: GoogleDriveService,
    private val tokenRepository: GoogleTokenRepository
) : SourceProvider {
    override val id = "google_drive"
    override val displayName = "Drive"
    override val icon = Icons.Default.CloudDownload

    override fun isAuthorized() = tokenRepository.hasTokens()

    @Composable
    override fun SelectorUI(onSourceAdded: (SourceItem) -> Unit, onDismiss: () -> Unit) {
        val accessToken = tokenRepository.getAccessToken()
        val scope = rememberCoroutineScope()
        val handler =
            remember(ingestionAgent, scope) { SourceIngestionHandler(ingestionAgent, scope) }
        var isIngesting by remember { mutableStateOf(false) }

        if (isIngesting) {
            IngestingProgressDialog(
                title = "Reading Drive File",
                message = "Downloading and analyzing file..."
            )
        } else if (accessToken == null) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Google Account Required") },
                text = { Text("Please link your Google account in Settings to use Drive.") },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        } else {
            DrivePickerDialog(
                driveService = driveService,
                onDismiss = onDismiss,
                onFileSelected = { file ->
                    handler.ingestDriveFile(
                        file = file,
                        onStart = { isIngesting = true },
                        onSuccess = onSourceAdded,
                        onFailure = onDismiss,
                        onFinish = { isIngesting = false }
                    )
                }
            )
        }
    }
}
