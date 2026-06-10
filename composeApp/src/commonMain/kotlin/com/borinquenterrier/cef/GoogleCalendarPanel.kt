package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun GoogleCalendarPanel(
    container: DependencyContainer,
    isGoogleLinked: Boolean,
    isBusy: Boolean,
    loginError: String?,
    googleCalendarId: String,
    googleCalendarName: String,
    calendars: List<RemoteCalendarMetadata>,
    isLoadingCalendars: Boolean,
    calendarLoadError: String?,
    onCalendarIdChange: (String, String) -> Unit,
    onCalendarsRefresh: suspend () -> Unit,
    onCalendarLoadError: (String?) -> Unit,
    scope: CoroutineScope
) {
    var showCreateCalendarDialog by remember { mutableStateOf(false) }
    var newCalendarNameInput by remember { mutableStateOf("") }
    var isCreatingCalendar by remember { mutableStateOf(false) }
    var createCalendarError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoogleLinked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGoogleLinked) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isGoogleLinked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Calendar & Drive", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                if (isGoogleLinked)
                    "Connected! We can now import documents and sync events."
                else
                    "Link your Google account to automatically import syllabi and sync your schedule.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            if (!isGoogleLinked) {
                Button(
                    onClick = {
                        scope.launch { container.googleAccountFlow.connect() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Connect Google Account")
                    }
                }
            } else {
                TextButton(
                    onClick = { container.googleAccountFlow.disconnect() },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect Account")
                }
            }

            if (isGoogleLinked) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Target Google Calendar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                GoogleCalendarSelector(
                    googleCalendarId = googleCalendarId,
                    googleCalendarName = googleCalendarName,
                    calendars = calendars,
                    isLoadingCalendars = isLoadingCalendars,
                    calendarLoadError = calendarLoadError,
                    onCalendarSelect = onCalendarIdChange,
                    onCreateClick = { showCreateCalendarDialog = true },
                    onRetryLoad = { scope.launch { onCalendarsRefresh() } },
                    onLoadError = onCalendarLoadError,
                    scope = scope,
                    container = container
                )
            }

            if (loginError != null) {
                Text(
                    loginError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showCreateCalendarDialog) {
        CreateCalendarDialog(
            newCalendarNameInput = newCalendarNameInput,
            onNameChange = { newCalendarNameInput = it },
            isCreating = isCreatingCalendar,
            createError = createCalendarError,
            onConfirm = {
                if (newCalendarNameInput.isNotBlank()) {
                    scope.launch {
                        isCreatingCalendar = true
                        createCalendarError = null
                        try {
                            val newId = container.syncService.createCalendar(newCalendarNameInput)
                            onCalendarIdChange(newId, newCalendarNameInput)

                            try {
                                onCalendarsRefresh()
                            } catch (e: Exception) {
                                onCalendarLoadError(CalendarErrorFormatter.format(e))
                            }

                            showCreateCalendarDialog = false
                            newCalendarNameInput = ""
                        } catch (e: Exception) {
                            createCalendarError = CalendarErrorFormatter.format(e)
                        } finally {
                            isCreatingCalendar = false
                        }
                    }
                }
            },
            onDismiss = {
                if (!isCreatingCalendar) {
                    showCreateCalendarDialog = false
                    newCalendarNameInput = ""
                    createCalendarError = null
                }
            }
        )
    }
}

@Composable
private fun GoogleCalendarSelector(
    googleCalendarId: String,
    googleCalendarName: String,
    calendars: List<RemoteCalendarMetadata>,
    isLoadingCalendars: Boolean,
    calendarLoadError: String?,
    onCalendarSelect: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onRetryLoad: () -> Unit,
    onLoadError: (String?) -> Unit,
    scope: CoroutineScope,
    container: DependencyContainer
) {
    if (isLoadingCalendars) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Loading available calendars...", style = MaterialTheme.typography.bodySmall)
        }
    } else if (calendarLoadError != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                calendarLoadError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetryLoad) {
                Text("Retry", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        val currentDisplayName =
            if (googleCalendarId == "default") "CEF Academic (Default)" else googleCalendarName

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = currentDisplayName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_calendar_picker_input"),
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Select Calendar"
                            )
                        }
                    }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .testTag("settings_calendar_dropdown_menu")
                ) {
                    DropdownMenuItem(
                        text = { Text("CEF Academic (Default)") },
                        onClick = {
                            onCalendarSelect("default", "CEF Academic")
                            expanded = false
                        },
                        modifier = Modifier.testTag("settings_calendar_option_default")
                    )
                    calendars.forEach { cal ->
                        DropdownMenuItem(
                            text = { Text(cal.name) },
                            onClick = {
                                onCalendarSelect(cal.id, cal.name)
                                expanded = false
                            },
                            modifier = Modifier.testTag("settings_calendar_option_${cal.name}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onCreateClick,
                    modifier = Modifier.testTag("settings_create_calendar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create New Calendar",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Create New Calendar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CreateCalendarDialog(
    newCalendarNameInput: String,
    onNameChange: (String) -> Unit,
    isCreating: Boolean,
    createError: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Google Calendar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter a name for the new Google Calendar:")
                OutlinedTextField(
                    value = newCalendarNameInput,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth().testTag("create_calendar_name_input"),
                    label = { Text("Calendar Name") },
                    placeholder = { Text("e.g., Study Calendar") },
                    singleLine = true,
                    enabled = !isCreating
                )
                if (createError != null) {
                    Text(
                        createError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = newCalendarNameInput.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}
