package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val authService = remember(settings) { GoogleAuthService(settings) }
    
    var isGoogleLinked by remember { mutableStateOf(tokenRepository.hasTokens()) }
    var apiKey by remember { mutableStateOf(settings.getString("GEMINI_API_KEY", "")) }
    var showAdvanced by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Setup & Connections", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connect your accounts to let the AI help you manage your studies.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Step 1: Google Account (The Primary Action)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isGoogleLinked) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGoogleLinked) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isGoogleLinked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Google Workspace", style = MaterialTheme.typography.titleLarge)
                }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isGoogleLinked) 
                        "Successfully linked! We can now reach into your Google Drive for syllabi and push events to your calendar."
                    else 
                        "Link your school or personal Google account to import documents and sync your schedule automatically."
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (!isGoogleLinked) {
                    Button(
                        onClick = { 
                            scope.launch {
                                try {
                                    val result = authService.login()
                                    tokenRepository.saveTokens(result.first, result.second)
                                    isGoogleLinked = true
                                    loginError = null
                                } catch (e: Exception) {
                                    loginError = e.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Google Account")
                    }
                } else {
                    TextButton(onClick = { /* Handle disconnect */ }) {
                        Text("Disconnect Account")
                    }
                }

                if (loginError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text(loginError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Advanced Settings (Hidden by default to reduce noise)
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(if (showAdvanced) Icons.Default.Info else Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (showAdvanced) "Hide Advanced Settings" else "Show Advanced AI Settings")
        }

        if (showAdvanced) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manual AI Configuration", style = MaterialTheme.typography.titleMedium)
                    Text("If you want to use a specific Gemini API key, enter it here:", style = MaterialTheme.typography.bodySmall)
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Paste your key here...") }
                    )
                    
                    Button(onClick = { settings.putString("GEMINI_API_KEY", apiKey) }) {
                        Text("Save Key")
                    }
                }
            }
        }
    }
}
