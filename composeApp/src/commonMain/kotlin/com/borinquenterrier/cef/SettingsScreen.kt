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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun SettingsScreen(
    container: DependencyContainer,
    modifier: Modifier = Modifier
) {
    val settings = container.settings
    val scope = rememberCoroutineScope()
    
    val googleFlow = container.googleAccountFlow
    val connectionState by googleFlow.state.collectAsState()

    var apiKey by remember { mutableStateOf(settings.getString("CEF_GEMINI_API_KEY", settings.getString("GEMINI_API_KEY", ""))) }
    var showAdvanced by remember { mutableStateOf(false) }

    val preferencesRepository = remember { container.preferencesRepository }
    var preferences by remember { mutableStateOf(preferencesRepository.getPreferences()) }

    var studyStartStr by remember { mutableStateOf(preferences.studyStartHour.toString()) }
    var studyEndStr by remember { mutableStateOf(preferences.studyEndHour.toString()) }
    var lunchStartStr by remember { mutableStateOf(preferences.lunchStartHour.toString()) }
    var lunchEndStr by remember { mutableStateOf(preferences.lunchEndHour.toString()) }
    var dinnerStartStr by remember { mutableStateOf(preferences.dinnerStartHour.toString()) }
    var dinnerEndStr by remember { mutableStateOf(preferences.dinnerEndHour.toString()) }
    var maxStudyBlockStr by remember { mutableStateOf(preferences.maxStudyBlockHours.toString()) }
    var preferredBreakStr by remember { mutableStateOf(preferences.preferredBreakMinutes.toString()) }

    fun parseAndSave() {
        val newPrefs = StudyPreferences(
            studyStartHour = studyStartStr.toIntOrNull() ?: preferences.studyStartHour,
            studyEndHour = studyEndStr.toIntOrNull() ?: preferences.studyEndHour,
            lunchStartHour = lunchStartStr.toIntOrNull() ?: preferences.lunchStartHour,
            lunchEndHour = lunchEndStr.toIntOrNull() ?: preferences.lunchEndHour,
            dinnerStartHour = dinnerStartStr.toIntOrNull() ?: preferences.dinnerStartHour,
            dinnerEndHour = dinnerEndStr.toIntOrNull() ?: preferences.dinnerEndHour,
            maxStudyBlockHours = maxStudyBlockStr.toIntOrNull() ?: preferences.maxStudyBlockHours,
            preferredBreakMinutes = preferredBreakStr.toIntOrNull() ?: preferences.preferredBreakMinutes
        )
        preferences = newPrefs
        preferencesRepository.savePreferences(newPrefs)
    }

    val isGoogleLinked = connectionState is GoogleConnectionState.Linked
    val isBusy = connectionState is GoogleConnectionState.Connecting
    val loginError = (connectionState as? GoogleConnectionState.Error)?.message

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Setup & Connections", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect your accounts to let the AI help you manage your studies.",
            style = MaterialTheme.typography.bodySmall,
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
                        apiKey = it
                        settings.putString("CEF_GEMINI_API_KEY", it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API Key") },
                    textStyle = MaterialTheme.typography.bodySmall,
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
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
                            scope.launch { googleFlow.connect() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Connect Google Account")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { googleFlow.disconnect() },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disconnect Account")
                    }
                }

                if (loginError != null) {
                    Text(loginError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Step 3: Study & Scheduling Preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Text("Daily Study Window", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = studyStartStr,
                        onValueChange = { 
                            studyStartStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("Start Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = studyEndStr,
                        onValueChange = { 
                            studyEndStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("End Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                // Lunch Break
                Text("Lunch Break Window", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = lunchStartStr,
                        onValueChange = { 
                            lunchStartStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("Start Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = lunchEndStr,
                        onValueChange = { 
                            lunchEndStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("End Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                // Dinner Break
                Text("Dinner & Exercise Break Window", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dinnerStartStr,
                        onValueChange = { 
                            dinnerStartStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("Start Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = dinnerEndStr,
                        onValueChange = { 
                            dinnerEndStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("End Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                // Study Block Settings
                Text("Study Block Limits", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = maxStudyBlockStr,
                        onValueChange = { 
                            maxStudyBlockStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("Max block duration (hours)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = preferredBreakStr,
                        onValueChange = { 
                            preferredBreakStr = it.filter { c -> c.isDigit() }
                            parseAndSave()
                        },
                        label = { Text("Preferred break (minutes)") },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
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
