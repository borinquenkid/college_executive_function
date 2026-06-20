package com.borinquenterrier.cef

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun FilePicker(show: Boolean, onFilesSelected: (List<String>) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            onFilesSelected(uris.map { it.toString() })
        }
    )

    if (show) {
        LaunchedEffect(Unit) {
            launcher.launch(
                arrayOf(
                    "text/calendar",
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain"
                )
            )
        }
    }
}
