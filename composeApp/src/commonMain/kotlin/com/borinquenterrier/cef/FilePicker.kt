package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

@Composable
expect fun FilePicker(show: Boolean, onFileSelected: (String?) -> Unit)
