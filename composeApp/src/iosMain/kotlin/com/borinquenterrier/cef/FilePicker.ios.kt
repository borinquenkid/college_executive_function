package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

@Composable
actual fun FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    // TODO: Implement a file picker for iOS. For now, this is a no-op.
    if (show) {
        onFileSelected(null)
    }
}
