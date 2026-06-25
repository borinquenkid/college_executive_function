@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DrivePickerDialog(
    driveService: GoogleDriveService,
    onDismiss: () -> Unit,
    onFileSelected: (DriveFile) -> Unit
) {
    var allFiles by remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<DriveFileType?>(null) }

    val fileFilter = remember { DriveFileFilter() }
    val displayedFiles = remember(allFiles, query, selectedType) {
        fileFilter.sort(fileFilter.filter(allFiles, query, selectedType))
    }

    LaunchedEffect(Unit) {
        try {
            allFiles = driveService.listFiles(GoogleDriveQueryBuilder.buildIngestibleFilesQuery())
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
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    errorMessage != null -> DrivePickerError(errorMessage!!)
                    else -> DrivePickerContent(
                        query = query,
                        onQueryChange = { query = it },
                        selectedType = selectedType,
                        onTypeSelected = { selectedType = it },
                        displayedFiles = displayedFiles,
                        hasAnyFiles = allFiles.isNotEmpty(),
                        onFileSelected = onFileSelected
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DrivePickerError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    Text("If this persists, try disconnecting and reconnecting your account in Settings.")
}

@Composable
private fun DrivePickerContent(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedType: DriveFileType?,
    onTypeSelected: (DriveFileType?) -> Unit,
    displayedFiles: List<DriveFile>,
    hasAnyFiles: Boolean,
    onFileSelected: (DriveFile) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search files…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        }
    )

    Spacer(Modifier.height(8.dp))
    DriveTypeFilterRow(selectedType = selectedType, onTypeSelected = onTypeSelected)
    Spacer(Modifier.height(4.dp))

    if (displayedFiles.isEmpty()) {
        Text(
            if (!hasAnyFiles) "No files found." else "No files match your search.",
            modifier = Modifier.padding(vertical = 8.dp)
        )
    } else {
        LazyColumn {
            items(displayedFiles) { file ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    supportingContent = { Text(DriveFileType.from(file)?.label ?: "File") },
                    leadingContent = { Icon(driveFileIcon(file), contentDescription = null) },
                    modifier = Modifier.clickable { onFileSelected(file) }
                )
            }
        }
    }
}

@Composable
private fun DriveTypeFilterRow(
    selectedType: DriveFileType?,
    onTypeSelected: (DriveFileType?) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onTypeSelected(null) },
            label = { Text("All") }
        )
        DriveFileType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(if (selectedType == type) null else type) },
                label = { Text(type.label) }
            )
        }
    }
}

private fun driveFileIcon(file: DriveFile): ImageVector = when (DriveFileType.from(file)) {
    DriveFileType.ICS -> Icons.Default.CalendarMonth
    else -> Icons.Default.Description
}
