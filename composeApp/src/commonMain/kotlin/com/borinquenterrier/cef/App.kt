package com.borinquenterrier.cef

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val platformName = getPlatform().name
        if (platformName.startsWith("Java") || platformName == "Web") { // Simple check for Desktop/Web
            DesktopApp()
        } else { // Android and iOS
            MobileApp()
        }
    }
}

@Composable
fun DesktopApp() {
    Row(modifier = Modifier.fillMaxSize()) {
        SourcesPanel(modifier = Modifier.weight(1f))
        ChatPanel(modifier = Modifier.weight(1f))
        StudioPanel(modifier = Modifier.weight(1f))
    }
}

@Composable
fun MobileApp() {
    var showSources by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sources Panel (Top)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = { showSources = !showSources }) {
                Icon(
                    imageVector = if (showSources) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showSources) "Hide Sources" else "Show Sources"
                )
            }
            AnimatedVisibility(visible = showSources) {
                SourcesPanel(modifier = Modifier.fillMaxWidth())
            }
        }

        // Chat Panel (Middle)
        ChatPanel(modifier = Modifier.weight(1f))

        // Studio Panel (Bottom)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = showStudio) {
                StudioPanel(modifier = Modifier.fillMaxWidth())
            }
            IconButton(onClick = { showStudio = !showStudio }) {
                Icon(
                    imageVector = if (showStudio) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (showStudio) "Hide Studio" else "Show Studio"
                )
            }
        }
    }
}
