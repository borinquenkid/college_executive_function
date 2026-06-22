@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun GoogleLinkPrompt(
    onLink: suspend () -> Boolean,
    onLinked: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.CloudCircle, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Sync with Google Calendar", style = MaterialTheme.typography.titleMedium)
            }
            Text("Link your account to import syllabi from Drive and push events to your Google Calendar.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    if (onLink()) {
                        onLinked()
                    }
                }
            }) {
                Text("Link Google Account")
            }
        }
    }
}
