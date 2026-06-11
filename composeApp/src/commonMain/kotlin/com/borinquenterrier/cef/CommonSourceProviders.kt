package com.borinquenterrier.cef

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

class LocalFileSourceProvider(
    private val ingestionAgent: IngestionAgent,
    private val aiService: AIService,
    private val filePicker: @Composable (onFileSelected: (String?) -> Unit) -> Unit = { onFileSelected ->
        FilePicker(
            show = true,
            onFileSelected = onFileSelected,
        )
    },
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
        val hasTriggered = remember { mutableStateOf(value = false) }
        val isIngesting = remember { mutableStateOf(value = false) }

        if (isIngesting.value) {
            IngestingProgressDialog(
                title = "Reading Document",
                message = "Extracting text and analyzing structure..."
            )
        }

        if (!hasTriggered.value) {
            filePicker { path ->
                hasTriggered.value = true
                if (path == null) {
                    onDismiss()
                } else {
                    handler.ingestLocalFile(
                        path = path,
                        onStart = { isIngesting.value = true },
                        onSuccess = onSourceAdded,
                        onFailure = onDismiss
                    ) { isIngesting.value = false }
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
        val isIngesting = remember { mutableStateOf(value = false) }

        val submitUrl = {
            if (url.isNotBlank()) {
                handler.ingestUrl(
                    url = url,
                    onStart = { isIngesting.value = true },
                    onSuccess = onSourceAdded,
                    onFailure = onDismiss
                ) { isIngesting.value = false }
            }
        }

        if (isIngesting.value) {
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
                                if ((keyEvent.key == Key.Enter) && (keyEvent.type == KeyEventType.KeyDown)) {
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
        val isIngesting = remember { mutableStateOf(value = false) }

        if (isIngesting.value) {
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
            ) { file ->
                handler.ingestDriveFile(
                    file = file,
                    onStart = { isIngesting.value = true },
                    onSuccess = onSourceAdded,
                    onFailure = onDismiss
                ) { isIngesting.value = false }
            }
        }
    }
}

@Composable
fun DrivePickerDialog(
    driveService: GoogleDriveService,
    onDismiss: () -> Unit,
    onFileSelected: (DriveFile) -> Unit
) {
    var files by remember { mutableStateOf<List<DriveFile>?>(null) }
    var isLoading by remember { mutableStateOf(value = true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val query = GoogleDriveQueryBuilder.buildIngestibleFilesQuery()
            files = driveService.listFiles(query)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to list files from Google Drive."
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select from Google Drive") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text("If this persists, try disconnecting and reconnecting your account in Settings.")
                } else if (files.isNullOrEmpty()) {
                    Text("No files found.")
                } else {
                    LazyColumn {
                        items(files!!) { file ->
                            Text(
                                text = file.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFileSelected(file) }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IngestingProgressDialog(title: String, message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(message)
            }
        },
        confirmButton = {}
    )
}
