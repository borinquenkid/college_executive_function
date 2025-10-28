package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    if (show) {
        val fileDialog = FileDialog(null as Frame?, "Select a file", FileDialog.LOAD)
        fileDialog.isVisible = true
        val file = fileDialog.file
        onFileSelected(file)
    }
}
