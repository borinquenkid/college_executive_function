@file:UiOnly
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
fun SemesterWindowCard(
    semesterStartStr: String,
    semesterEndStr: String,
    onSemesterStartChange: (String) -> Unit,
    onSemesterEndChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var validationError by remember(semesterStartStr, semesterEndStr) {
        mutableStateOf<String?>(null)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Semester Window", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Events from calendar sources outside this range are hidden. " +
                "Use YYYY-MM-DD format.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = semesterStartStr,
                    onValueChange = { onSemesterStartChange(it); validationError = null },
                    label = { Text("Start (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = semesterEndStr,
                    onValueChange = { onSemesterEndChange(it); validationError = null },
                    label = { Text("End (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val start = semesterStartStr.ifBlank { null }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val end = semesterEndStr.ifBlank { null }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    validationError = when {
                        semesterStartStr.isNotBlank() && start == null ->
                            "Invalid start date — use YYYY-MM-DD"
                        semesterEndStr.isNotBlank() && end == null ->
                            "Invalid end date — use YYYY-MM-DD"
                        start != null && end != null && end < start ->
                            "End date must be after start date"
                        else -> null
                    }
                    if (validationError == null) onSave()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Semester Window")
            }
        }
    }
}
