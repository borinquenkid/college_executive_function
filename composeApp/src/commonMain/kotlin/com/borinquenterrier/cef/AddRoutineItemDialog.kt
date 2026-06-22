@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRoutineItemDialog(onDismiss: () -> Unit, onSave: (TimeEvent) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var startTime by remember { mutableStateOf(LocalTime(10, 30)) }
    var endTime by remember { mutableStateOf(LocalTime(11, 45)) }
    var startDate by remember { mutableStateOf(LocalDate(2024, 8, 26)) }
    var endDate by remember { mutableStateOf(LocalDate(2024, 12, 13)) }

    var showDatePicker by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    var showTimePicker by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Routine Item", style = MaterialTheme.typography.headlineSmall)

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Repeats on:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                            label = { Text(day.name.take(3)) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClickableField(
                        value = startTime.toString(),
                        label = "Start Time",
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    ClickableField(
                        value = endTime.toString(),
                        label = "End Time",
                        onClick = { showTimePicker = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClickableField(
                        value = startDate.toString(),
                        label = "Start Date",
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    ClickableField(
                        value = endDate.toString(),
                        label = "End Date",
                        onClick = { showDatePicker = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val newEvent = TimeEvent(
                            title = title,
                            source = EventSource.ROUTINE,
                            startTime = startTime,
                            endTime = endTime,
                            date = startDate,
                            recurrence = Recurrence(
                                daysOfWeek = selectedDays.toList(),
                                startDate = startDate,
                                endDate = endDate
                            )
                        )
                        onSave(newEvent)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showDatePicker != null) {
        val isStartDate = showDatePicker == true
        val initialDate = if (isStartDate) startDate else endDate
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val selectedDate =
                                Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                            if (isStartDate) {
                                startDate = selectedDate
                            } else {
                                endDate = selectedDate
                            }
                        }
                        showDatePicker = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = null }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker != null) {
        val isStartTime = showTimePicker == true
        val initialTime = if (isStartTime) startTime else endTime

        TimePickerDialog(
            initialTime = initialTime,
            onDismiss = { showTimePicker = null },
            onConfirm = { newTime ->
                if (isStartTime) {
                    startTime = newTime
                } else {
                    endTime = newTime
                }
                showTimePicker = null
            },
            title = if (isStartTime) "Select Start Time" else "Select End Time"
        )
    }
}

@Composable
private fun ClickableField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                TimeInput(state = timePickerState)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        onConfirm(
                            LocalTime(
                                timePickerState.hour,
                                timePickerState.minute
                            )
                        )
                    }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
