@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Resolves the deliverables the pipeline caught with an ungrounded date. Shows the source text
 * the item came from next to a date picker so the user can confirm a real date (re-admitting it
 * to the push list) or discard it as fabricated. One item at a time; the host re-renders the next
 * as [items] shrinks. Replaces the dead-end conflict dialog with something actionable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateResolutionDialog(
    items: List<DateResolutionItem>,
    onConfirm: (DateResolutionItem, LocalDate) -> Unit,
    onDiscard: (DateResolutionItem) -> Unit,
) {
    val item = items.firstOrNull() ?: return

    // Reset the picked date whenever we advance to a new item.
    var pickedDate by remember(item) { mutableStateOf(DateResolutionPresenter.initialDate(item)) }
    var showPicker by remember(item) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* modal: each item must be confirmed or discarded */ },
        title = { Text("Confirm a date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.event.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        DateResolutionPresenter.evidenceText(item),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().testTag("date_resolution_pick")
                ) {
                    Text("Date: $pickedDate")
                }
                if (items.size > 1) {
                    Text(
                        "${items.size - 1} more to review after this",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(item, pickedDate) },
                modifier = Modifier.testTag("date_resolution_confirm")
            ) { Text("Confirm date") }
        },
        dismissButton = {
            TextButton(
                onClick = { onDiscard(item) },
                modifier = Modifier.testTag("date_resolution_discard")
            ) { Text("Discard") }
        }
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = pickedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        pickedDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
