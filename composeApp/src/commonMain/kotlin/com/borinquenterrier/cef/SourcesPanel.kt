package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesPanel(
    modifier: Modifier = Modifier,
    sourceItems: List<SourceItem>,
    selectedSource: SourceItem?,
    onSourceSelected: (SourceItem) -> Unit,
    onSourceAdded: (SourceItem) -> Unit,
    onUrlSourceAdded: (String) -> Unit
) {
    var showFilePicker by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showDrivePicker by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val settings = rememberSettings()
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val driveService = remember { GoogleDriveService(HttpClient { 
        install(ContentNegotiation) { json() } 
    }) }
    val fileReader = rememberLocalFileReader()
    val docxReader = rememberDocxReader()
    val pdfReader = rememberPdfReader()

    Column(
        modifier = modifier
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { showFilePicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add File")
                Text("File")
            }
            Button(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.Link, contentDescription = "Add from URL")
                Text("URL")
            }
            Button(onClick = { showDrivePicker = true }) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Import from Drive")
                Text("Drive")
            }
        }

        LazyColumn {
            items(sourceItems) { item ->
                SourceItemView(
                    item = item,
                    isSelected = item == selectedSource,
                    onClick = { onSourceSelected(item) }
                )
            }
        }
    }

    if (showFilePicker) {
        FilePicker(show = true) { path ->
            showFilePicker = false
            if (path != null) {
                scope.launch {
                    val fileName = path.substringAfterLast("/").substringAfterLast("\\")
                    val content = when {
                        fileName.lowercase().endsWith(".docx") -> docxReader.extractText(path)
                        fileName.lowercase().endsWith(".pdf") -> pdfReader.extractText(path)
                        else -> fileReader.readText(path)
                    }
                    onSourceAdded(SourceItem(fileName, content))
                }
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
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
                TextButton(
                    onClick = {
                        onUrlSourceAdded(url)
                        showUrlDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDrivePicker) {
        val accessToken = tokenRepository.getAccessToken()
        if (accessToken == null) {
            AlertDialog(
                onDismissRequest = { showDrivePicker = false },
                title = { Text("Google Account Required") },
                text = { Text("Please link your Google account in Settings to use Drive.") },
                confirmButton = {
                    TextButton(onClick = { showDrivePicker = false }) { Text("OK") }
                }
            )
        } else {
            DrivePickerDialog(
                driveService = driveService,
                accessToken = accessToken,
                onDismiss = { showDrivePicker = false },
                onFileSelected = { file ->
                    scope.launch {
                        val content = driveService.getFileContent(accessToken, file.id, file.mimeType)
                        onSourceAdded(SourceItem(file.name, content))
                        showDrivePicker = false
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
