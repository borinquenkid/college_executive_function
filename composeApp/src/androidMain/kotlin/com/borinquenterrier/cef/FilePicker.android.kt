package com.borinquenterrier.cef

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            onFileSelected(uri?.toString())
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
