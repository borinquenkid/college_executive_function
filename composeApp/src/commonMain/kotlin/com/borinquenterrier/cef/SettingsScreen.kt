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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

@Composable
fun SettingsScreen(
    tokenRepository: GoogleTokenRepository,
    authService: GoogleAuthService,
    driveService: GoogleDriveService,
    modifier: Modifier = Modifier
) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    
    val isGoogleLinked by tokenRepository.isLinked.collectAsState()
    var apiKey by remember { mutableStateOf(settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))) }
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

        // Step 1: Gemini AI (The Engine)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (apiKey.isNotBlank()) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else 
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (apiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (apiKey.isNotBlank()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("AI Brain (Gemini)", style = MaterialTheme.typography.titleLarge)
                }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    "To keep this app free and private, it uses your own free AI key from Google. It takes 30 seconds to set up."
                )
                
                Spacer(Modifier.height(16.dp))

                if (apiKey.isBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Click 'Get Free Key' to open Google AI Studio.")
                        Text("2. Click 'Create API key' and copy it.")
                        Text("3. Paste the key below.")
                        
                        Button(
                            onClick = { PlatformUtils.openBrowser("https://aistudio.google.com/app/apikey") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Get Free API Key")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { 
                        apiKey = it
                        settings.putString("CEF_GEMINI_API_KEY", it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Paste your key here...") },
                    trailingIcon = {
                        if (apiKey.isNotBlank()) {
                            IconButton(onClick = { 
                                apiKey = ""
                                settings.putString("CEF_GEMINI_API_KEY", "")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear, 
                                    contentDescription = "Clear", 
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        }

        // Step 2: Google Account (The Storage)
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
                    Text("Calendar & Drive", style = MaterialTheme.typography.titleLarge)
                }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isGoogleLinked) 
                        "Connected! We can now import documents and sync events."
                    else 
                        "Link your Google account to automatically import syllabi and sync your schedule."
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (!isGoogleLinked) {
                    Button(
                        onClick = { 
                            scope.launch {
                                try {
                                    println("[Settings] Starting login flow...")
                                    val result = authService.login()
                                    println("[Settings] Login successful. Saving tokens...")
                                    tokenRepository.saveTokens(result.first, result.second)
                                    
                                    println("[Settings] Validating Drive connection...")
                                    val isValid = driveService.validateConnection(result.first)
                                    println("[Settings] Drive validation result: $isValid")
                                    
                                    if (isValid) {
                                        loginError = null
                                    } else {
                                        tokenRepository.clearTokens()
                                        loginError = "Connected to Google, but Drive access failed. Please ensure you checked the permission box in the browser."
                                    }
                                } catch (e: Exception) {
                                    println("[Settings] Login failed with exception: ${e.message}")
                                    loginError = e.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Google Account")
                    }
                } else {
                    TextButton(onClick = { 
                        println("[Settings] Wiping local session...")
                        authService.logout()
                        tokenRepository.clearTokens()
                    }) {
                        Text("Disconnect Account")
                    }
                }

                if (loginError != null) {
                    Text(loginError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Advanced Settings (Minimized)
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(if (showAdvanced) Icons.Default.Info else Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (showAdvanced) "Advanced" else "Advanced")
        }

        if (showAdvanced) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var debugMode by remember { mutableStateOf(settings.getBoolean("DEBUG_MODE", false)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Debug Logs", modifier = Modifier.weight(1f))
                        Switch(checked = debugMode, onCheckedChange = { 
                            debugMode = it
                            settings.putBoolean("DEBUG_MODE", it)
                        })
                    }
                }
            }
        }
    }
}
