package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StudyPreferencesPanel(
    studyStartStr: String,
    studyEndStr: String,
    lunchStartStr: String,
    lunchEndStr: String,
    dinnerStartStr: String,
    dinnerEndStr: String,
    maxStudyBlockStr: String,
    preferredBreakStr: String,
    onStudyStartChange: (String) -> Unit,
    onStudyEndChange: (String) -> Unit,
    onLunchStartChange: (String) -> Unit,
    onLunchEndChange: (String) -> Unit,
    onDinnerStartChange: (String) -> Unit,
    onDinnerEndChange: (String) -> Unit,
    onMaxStudyBlockChange: (String) -> Unit,
    onPreferredBreakChange: (String) -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Study & Scheduling Preferences", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                "Customize your daily study window, break times, and study blocks. The AI will respect these constraints when generating your schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Working Hours Window
            Text(
                "Daily Study Window",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = studyStartStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onStudyStartChange(filtered)
                    },
                    label = { Text("Start Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = studyEndStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onStudyEndChange(filtered)
                    },
                    label = { Text("End Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Lunch Break
            Text(
                "Lunch Break",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = lunchStartStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onLunchStartChange(filtered)
                    },
                    label = { Text("Start Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = lunchEndStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onLunchEndChange(filtered)
                    },
                    label = { Text("End Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Dinner Break
            Text(
                "Dinner Break",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = dinnerStartStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onDinnerStartChange(filtered)
                    },
                    label = { Text("Start Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = dinnerEndStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onDinnerEndChange(filtered)
                    },
                    label = { Text("End Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Study Block & Break Preferences
            Text(
                "Study Blocks & Breaks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = maxStudyBlockStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c == '.' }
                        onMaxStudyBlockChange(filtered)
                    },
                    label = { Text("Max Block (hours)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = preferredBreakStr,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        onPreferredBreakChange(filtered)
                    },
                    label = { Text("Break Length (min)") },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
