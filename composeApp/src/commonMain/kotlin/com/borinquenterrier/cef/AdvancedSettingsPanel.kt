package com.borinquenterrier.cef

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings

@Composable
fun AdvancedSettingsPanel(
    settings: Settings,
    shareAnonymousBugReports: Boolean,
    onBugReportsChange: (Boolean) -> Unit
) {
    var debugMode by remember { mutableStateOf(settings.getBoolean("DEBUG_MODE", false)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Debug Logs", modifier = Modifier.weight(1f))
                Switch(checked = debugMode, onCheckedChange = { 
                    debugMode = it
                    settings.putBoolean("DEBUG_MODE", it)
                })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share Anonymous Bug Reports", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Help us improve the app by automatically sharing anonymized error logs when a crash or failure occurs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = shareAnonymousBugReports, onCheckedChange = { 
                        onBugReportsChange(it)
                    })
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What is shared:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("• Platform type (e.g., JVM/Desktop, Android, iOS)", style = MaterialTheme.typography.bodySmall)
                        Text("• Exception name & generic context descriptions", style = MaterialTheme.typography.bodySmall)
                        Text("• Code stack traces & line numbers where the failure occurred", style = MaterialTheme.typography.bodySmall)
                        Text("• Basic anonymized telemetry metrics (JSON parsing issues, rate limiting counts)", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(4.dp))

                        Text("What is NEVER shared:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFE53935))
                        Text("• Your calendar event contents, descriptions, or titles", style = MaterialTheme.typography.bodySmall)
                        Text("• Your personal academic syllabus files, documents, or raw text", style = MaterialTheme.typography.bodySmall)
                        Text("• API keys, passwords, credentials, or Google account tokens", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
