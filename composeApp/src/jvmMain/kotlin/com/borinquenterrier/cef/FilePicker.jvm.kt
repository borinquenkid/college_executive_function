package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    LaunchedEffect(show) {
        if (show) {
            val fileDialog = FileDialog(null as Frame?, "Select a File", FileDialog.LOAD)
            fileDialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".ics") || lower.endsWith(".pdf") ||
                        lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".gdoc")
            }
            fileDialog.isVisible = true
            val directory = fileDialog.directory
            val file = fileDialog.file
            val fullPath = if (directory != null && file != null) {
                java.io.File(directory, file).absolutePath
            } else null
            onFileSelected(fullPath)
        }
    }
}
