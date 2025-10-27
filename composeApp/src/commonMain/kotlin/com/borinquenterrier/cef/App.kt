package com.borinquenterrier.cef

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borinquenterrier.cef.ui.theme.CollegeExecutiveFunctionTheme
import com.borinquenterrier.college_executive_function.getPlatform
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    CollegeExecutiveFunctionTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("College Executive Function") })
            }
        ) { paddingValues ->
            val platformName = getPlatform().name
            val modifier = Modifier.fillMaxSize().padding(paddingValues)
            if (platformName.startsWith("Java") || platformName == "Web") { // Simple check for Desktop/Web
                DesktopApp(modifier)
            } else { // Android and iOS
                MobileApp(modifier)
            }
        }
    }
}

@Composable
fun DesktopApp(modifier: Modifier = Modifier) {
    var showSources by remember { mutableStateOf(true) }
    var showStudio by remember { mutableStateOf(true) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = showSources, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourcesPanel(modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(24.dp)
                        .clickable { showSources = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "Hide Sources"
                    )
                }
            }
        }

        if (!showSources) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .clickable { showSources = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Show Sources"
                )
            }
        }

        ChatPanel(modifier = Modifier.weight(2f))

        if (!showStudio) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .clickable { showStudio = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Show Studio"
                )
            }
        }

        AnimatedVisibility(visible = showStudio, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(24.dp)
                        .clickable { showStudio = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Hide Studio"
                    )
                }
                StudioPanel(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MobileApp(modifier: Modifier = Modifier) {
    var showSources by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Sources Panel (Top)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = { showSources = !showSources }) {
                Icon(
                    imageVector = if (showSources) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
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
                    imageVector = if (showStudio) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (showStudio) "Hide Studio" else "Show Studio"
                )
            }
        }
    }
}
