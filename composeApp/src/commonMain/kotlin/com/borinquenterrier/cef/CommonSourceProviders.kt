package com.borinquenterrier.cef

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class LocalFileSourceProvider(
    private val ingestionAgent: IngestionAgent,
    private val aiService: AIService
) : SourceProvider {
    override val id = "local_file"
    override val displayName = "File"
    override val icon = Icons.Default.Add

    override fun isAuthorized() = aiService.isConfigured()

    @Composable
    override fun SelectorUI(onSourceAdded: (SourceItem) -> Unit, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        var hasTriggered by remember { mutableStateOf(false) }
        
        if (!hasTriggered) {
            FilePicker(show = true) { path ->
                hasTriggered = true
                if (path == null) {
                    onDismiss()
                } else {
                    scope.launch {
                        val source = ingestionAgent.addLocalFile(path)
                        onSourceAdded(source)
                    }
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
        var url by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add Source from URL") },
            text = {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isNotBlank()) {
                        scope.launch {
                            val source = ingestionAgent.addUrl(url)
                            onSourceAdded(source)
                        }
                    }
                }) {
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
        
        if (accessToken == null) {
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
                    scope.launch {
                        val source = ingestionAgent.addDriveFile(file)
                        onSourceAdded(source)
                    }
                }
            )
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
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val query = "mimeType = 'application/vnd.google-apps.document' " +
                    "or mimeType = 'application/pdf' " +
                    "or mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' " +
                    "or mimeType = 'text/plain' " +
                    "or name contains '.ics'"
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
