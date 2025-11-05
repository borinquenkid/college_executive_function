package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val settings = rememberSettings()
    var apiKey by remember { mutableStateOf(settings.getString("GEMINI_API_KEY", "")) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Enter your Gemini API Key:")
        TextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { settings.putString("GEMINI_API_KEY", apiKey) }) {
            Text("Save")
        }
    }
}
