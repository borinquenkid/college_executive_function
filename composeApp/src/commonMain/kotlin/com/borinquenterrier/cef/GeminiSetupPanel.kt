package com.borinquenterrier.cef

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings

@Composable
fun GeminiSetupPanel(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    settings: Settings
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (apiKey.isNotBlank())
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (apiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (apiKey.isNotBlank()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("AI Brain (Gemini)", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "To keep this app free and private, it uses your own free AI key from Google.",
                style = MaterialTheme.typography.bodySmall
            )

            if (apiKey.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { PlatformUtils.openBrowser("https://aistudio.google.com/app/apikey") },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Get Free API Key", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    onApiKeyChange(it)
                    settings.putString("CEF_GEMINI_API_KEY", it)
                },
                modifier = Modifier.fillMaxWidth().testTag("settings_api_key_input"),
                label = { Text("Gemini API Key") },
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = { Text("Paste your key here...") },
                trailingIcon = {
                    if (apiKey.isNotBlank()) {
                        IconButton(
                            onClick = { 
                                onApiKeyChange("")
                                settings.putString("CEF_GEMINI_API_KEY", "")
                            },
                            modifier = Modifier.testTag("settings_api_key_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}
