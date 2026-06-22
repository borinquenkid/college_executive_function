@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun UniversalHomeLayout(container: DependencyContainer) {
    val appController = container.appController
    val sourceItems by appController.sourceItems.asStateFlow().collectAsState()
    val selectedSource by appController.selectedSource.asStateFlow().collectAsState()

    var showSources by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }

    val sourceProviders = remember(container) {
        listOf(
            LocalFileSourceProvider(container.ingestionAgent, container.aiService),
            UrlSourceProvider(container.ingestionAgent, container.aiService),
            GoogleDriveSourceProvider(
                container.ingestionAgent,
                container.driveService,
                container.tokenRepository
            )
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

        // --- BASE LAYER: THE PERMANENT CHAT CANVAS ---
        // This is always the foundation. It fills the screen and is never squished.
        ChatPanel(
            modifier = Modifier.fillMaxSize(),
            appController = appController
        )

        // --- LAYER 3: ADAPTIVE OVERLAY DRAWERS ---
        // Sources Overlay (Left)
        androidx.compose.animation.AnimatedVisibility(
            visible = showSources,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().width(320.dp).shadow(16.dp)
                    .testTag("sources_drawer"),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box {
                    SourcesPanel(
                        sourceItems = sourceItems,
                        selectedSource = selectedSource,
                        onSourceSelected = { appController.selectSource(it); showSources = false },
                        onSourceAdded = { source ->
                            appController.addSource(source)
                        },
                        onSourceDeleted = { source ->
                            appController.deleteSource(source)
                        },
                        onSourceReanalyzed = { source ->
                            appController.reanalyzeSource(source)
                        },
                        providers = sourceProviders
                    )
                    // Close shortcut
                    IconButton(
                        onClick = { showSources = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        // Studio Overlay (Right)
        androidx.compose.animation.AnimatedVisibility(
            visible = showStudio,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().width(360.dp).shadow(16.dp)
                    .testTag("studio_drawer"),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box {
                    StudioPanel(
                        selectedSource = selectedSource,
                        calendarAgent = container.calendarAgent,
                        container = container
                    )
                    // Close shortcut
                    IconButton(
                        onClick = { showStudio = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        // --- LAYER 2: EDGE NAVIGATION (THE FLOATING CONTROLS) ---
        // Declared after the drawers so the triggers stay on top and remain clickable
        // (and thus able to collapse their own drawer) while it's open.
        // Left Edge: Sources Trigger
        Box(
            Modifier.fillMaxHeight().align(Alignment.CenterStart).padding(start = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { showSources = !showSources; if (showSources) showStudio = false },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp).testTag("sources_toggle_button")
            ) {
                Icon(
                    if (showSources) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.Default.Menu,
                    "Sources"
                )
            }
        }

        // Right Edge: Studio Trigger
        Box(
            Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { showStudio = !showStudio; if (showStudio) showSources = false },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(48.dp).testTag("studio_toggle_button")
            ) {
                Icon(
                    if (showStudio) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.Build,
                    "Studio"
                )
            }
        }
    }
}
