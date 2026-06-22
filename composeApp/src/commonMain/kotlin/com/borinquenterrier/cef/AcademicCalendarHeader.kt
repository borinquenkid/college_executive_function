@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AcademicCalendarHeader(
    isGoogleLinked: Boolean,
    isSyncing: Boolean,
    onNavigateRoutine: () -> Unit,
    onNavigateHome: () -> Unit,
    onSync: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Button(
            onClick = onNavigateRoutine,
            modifier = Modifier.weight(1f)
        ) {
            Text("Weekly Routine")
        }

        Button(
            onClick = onNavigateHome,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Add Source")
        }

        if (isGoogleLinked) {
            IconButton(
                onClick = onSync,
                enabled = !isSyncing
            ) {
                if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.Sync, contentDescription = "Sync Now")
            }
        }
    }
}
