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
    private val fileReader: LocalFileReader,
    private val docxReader: DocxReader,
    private val pdfReader: PdfReader,
    private val aiService: AIService
) : SourceProvider {
    override val id = "local_file"
    override val displayName = "File"
    override val icon = Icons.Default.Add

    override fun isAuthorized() = aiService.isConfigured()

    @Composable
    override fun SelectorUI(onSourceAdded: (SourceItem) -> Unit, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        FilePicker(show = true) { path ->
            if (path == null) {
                onDismiss()
            } else {
                scope.launch {
                    val fileName = path.substringAfterLast("/").substringAfterLast("\\")
                    val parts = when {
                        fileName.lowercase().endsWith(".docx") -> docxReader.readSource(path)
                        fileName.lowercase().endsWith(".pdf") -> pdfReader.readSource(path)
                        fileName.lowercase().endsWith(".ics") -> {
                            val raw = fileReader.readText(path)
                            IcsCalendarSource(raw).readSource()
                        }
                        else -> SourceProcessor.process(fileReader.readText(path))
                    }
                    onSourceAdded(SourceItem(fileName, parts))
                }
            }
        }
    }
}

class UrlSourceProvider(
    private val webReader: WebSourceReader,
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
                            val rawContent = webReader.readTextFromUrl(url)
                            val parts = if (url.lowercase().endsWith(".ics")) {
                                IcsCalendarSource(rawContent).readSource()
                            } else {
                                SourceProcessor.process(rawContent)
                            }
                            onSourceAdded(SourceItem(url, parts))
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
                accessToken = accessToken,
                onDismiss = onDismiss,
                onFileSelected = { file ->
                    scope.launch {
                        val rawContent = driveService.getFileContent(accessToken, file.id, file.mimeType)
                        val parts = when {
                            file.name.lowercase().endsWith(".ics") -> IcsCalendarSource(rawContent).readSource()
                            // Note: Google Drive service would need to be updated to support PDF/Docx chunking directly
                            // For now, we treat them as text if the mimeType allows, or just chunk the raw output
                            else -> SourceProcessor.process(rawContent)
                        }
                        onSourceAdded(SourceItem(file.name, parts))
                    }
                }
            )
        }
    }
}

@Composable
fun DrivePickerDialog(
    driveService: GoogleDriveService,
    accessToken: String,
    onDismiss: () -> Unit,
    onFileSelected: (DriveFile) -> Unit
) {
    var files by remember { mutableStateOf<List<DriveFile>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val query = "mimeType = 'application/vnd.google-apps.document' " +
                "or mimeType = 'application/pdf' " +
                "or mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' " +
                "or mimeType = 'text/plain' " +
                "or name contains '.ics'"
        files = driveService.listFiles(accessToken, query)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select from Google Drive") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
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
