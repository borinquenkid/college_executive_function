@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * A clickable field that opens a native calendar picker instead of accepting typed text, so
 * users never have to match an exact "YYYY-MM-DD" format. Stores/emits ISO-8601 strings
 * (LocalDate.toString()) to stay compatible with existing String-based preference storage,
 * but displays a locale-neutral "Jul 5, 2026" style label (see DateDisplayFormatter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedDate = remember(value) {
        value.ifBlank { null }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                shape = MaterialTheme.shapes.small
            )
            .clickable { showPicker = true }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                selectedDate?.let { DateDisplayFormatter.format(it) } ?: "Tap to select",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (showPicker) {
        val initialDate = selectedDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val picked = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                        onValueChange(picked.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
