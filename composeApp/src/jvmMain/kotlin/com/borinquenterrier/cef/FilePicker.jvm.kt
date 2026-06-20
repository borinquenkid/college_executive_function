package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun FilePicker(show: Boolean, onFilesSelected: (List<String>) -> Unit) {
    LaunchedEffect(show) {
        if (show) {
            val fileDialog = FileDialog(null as Frame?, "Select Files", FileDialog.LOAD)
            fileDialog.isMultipleMode = true
            fileDialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".ics") || lower.endsWith(".pdf") ||
                        lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".gdoc")
            }
            fileDialog.isVisible = true
            val files = fileDialog.files?.map { it.absolutePath } ?: emptyList()
            onFilesSelected(files)
        }
    }
}
