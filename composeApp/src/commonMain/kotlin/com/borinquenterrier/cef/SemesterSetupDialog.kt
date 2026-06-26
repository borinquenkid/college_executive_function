@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
fun SemesterSetupDialog(
    onSave: (start: String, end: String) -> Unit,
    onSkip: () -> Unit
) {
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Set Semester Window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Events outside your semester dates will be hidden. " +
                    "This avoids showing events from other terms in your schedule.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; validationError = null },
                    label = { Text("Semester start (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; validationError = null },
                    label = { Text("Semester end (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val start = runCatching { LocalDate.parse(startText) }.getOrNull()
                val end = runCatching { LocalDate.parse(endText) }.getOrNull()
                validationError = when {
                    start == null -> "Enter a valid start date, e.g. 2026-06-09"
                    end == null -> "Enter a valid end date, e.g. 2026-08-08"
                    end < start -> "End date must be after start date"
                    else -> null
                }
                if (validationError == null) onSave(startText, endText)
            }) {
                Text("Save & Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
}
