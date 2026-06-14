package com.borinquenterrier.cef

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, kotlin.time.ExperimentalTime::class)
@Composable
fun AddRoutineItemDialog(onDismiss: () -> Unit, onSave: (TimeEvent) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var title by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var startTime by remember { mutableStateOf(LocalTime(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime(10, 0)) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }

    val isFormValid = title.isNotBlank() && selectedDays.isNotEmpty() && (startTime < endTime) && (startDate <= endDate)

    val showDatePicker = remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    val showTimePicker = remember { mutableStateOf<Boolean?>(null) } // true: start, false: end

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add Routine Item", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                        onClick = { showTimePicker.value = true },
                        modifier = Modifier.weight(1f)
                    )
                    ClickableField(
                        value = endTime.toString(),
                        label = "End Time",
                        onClick = { showTimePicker.value = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClickableField(
                        value = startDate.toString(),
                        label = "Start Date",
                        onClick = { showDatePicker.value = true },
                        modifier = Modifier.weight(1f)
                    )
                    ClickableField(
                        value = endDate.toString(),
                        label = "End Date",
                        onClick = { showDatePicker.value = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
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
                            onDismiss()
                        },
                        enabled = isFormValid
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showDatePicker.value != null) {
        val isStartDate = showDatePicker.value == true
        val initialDate = if (isStartDate) startDate else endDate
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = null },
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
                        showDatePicker.value = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker.value = null }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker.value != null) {
        val isStartTime = showTimePicker.value == true
        val initialTime = if (isStartTime) startTime else endTime

        TimePickerDialog(
            initialTime = initialTime,
            onDismiss = { showTimePicker.value = null },
            onConfirm = { newTime ->
                if (isStartTime) {
                    startTime = newTime
                } else {
                    endTime = newTime
                }
                showTimePicker.value = null
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
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
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
                    TextButton(
                        onClick = {
                            onConfirm(
                                LocalTime(
                                    timePickerState.hour,
                                    timePickerState.minute
                                )
                            )
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
