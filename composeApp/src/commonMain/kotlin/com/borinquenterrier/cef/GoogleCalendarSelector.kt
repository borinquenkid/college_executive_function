package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun GoogleCalendarSelector(
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
        val currentDisplayName = CalendarDisplayName.resolve(googleCalendarId, googleCalendarName)

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
