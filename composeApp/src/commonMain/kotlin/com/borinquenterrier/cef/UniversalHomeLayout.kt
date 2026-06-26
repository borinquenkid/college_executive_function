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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioFab(isOpen: Boolean, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("AI Studio") } },
        state = rememberTooltipState()
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp).testTag("studio_toggle_button")
        ) {
            Icon(
                if (isOpen) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.Build,
                contentDescription = "Open AI Studio panel"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalHomeLayout(container: DependencyContainer) {
    val appController = container.appController
    val sourceItems by appController.sourceItems.asStateFlow().collectAsState()
    val selectedSource by appController.selectedSource.asStateFlow().collectAsState()

    var showSources by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val preferences by container.preferencesFlow.collectAsState()
    var pendingSemesterSource by remember { mutableStateOf<SourceItem?>(null) }

    pendingSemesterSource?.let { pending ->
        if (preferences.semesterStart != null && preferences.semesterEnd != null) {
            pendingSemesterSource = null
            appController.addSource(pending)
        } else {
            SemesterSetupDialog(
                onSave = { start, end ->
                    scope.launch {
                        val prefs = container.preferencesRepository.getPreferences()
                        container.preferencesRepository.savePreferences(
                            prefs.copy(semesterStart = start, semesterEnd = end)
                        )
                        val src = pendingSemesterSource
                        pendingSemesterSource = null
                        if (src != null) appController.addSource(src)
                    }
                },
                onSkip = { pendingSemesterSource = null }
            )
        }
    }

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
        if (isDesktop) {
            // Desktop: slide-in side panels
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
                                if (preferences.semesterStart != null && preferences.semesterEnd != null) {
                                    appController.addSource(source)
                                } else {
                                    pendingSemesterSource = source
                                }
                            },
                            onSourceDeleted = { appController.deleteSource(it) },
                            onSourceReanalyzed = { appController.reanalyzeSource(it) },
                            providers = sourceProviders
                        )
                        IconButton(
                            onClick = { showSources = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) { Icon(Icons.Default.Close, null) }
                    }
                }
            }

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
                        IconButton(
                            onClick = { showStudio = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) { Icon(Icons.Default.Close, null) }
                    }
                }
            }
        } else {
            // Mobile: full-width modal bottom sheets
            if (showSources) {
                ModalBottomSheet(
                    onDismissRequest = { showSources = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    modifier = Modifier.testTag("sources_drawer")
                ) {
                    SourcesPanel(
                        sourceItems = sourceItems,
                        selectedSource = selectedSource,
                        onSourceSelected = { appController.selectSource(it); showSources = false },
                        onSourceAdded = { source ->
                            showSources = false
                            if (preferences.semesterStart != null && preferences.semesterEnd != null) {
                                appController.addSource(source)
                            } else {
                                pendingSemesterSource = source
                            }
                        },
                        onSourceDeleted = { appController.deleteSource(it) },
                        onSourceReanalyzed = { appController.reanalyzeSource(it) },
                        providers = sourceProviders
                    )
                }
            }

            if (showStudio) {
                ModalBottomSheet(
                    onDismissRequest = { showStudio = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    modifier = Modifier.testTag("studio_drawer")
                ) {
                    StudioPanel(
                        selectedSource = selectedSource,
                        calendarAgent = container.calendarAgent,
                        container = container
                    )
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
            StudioFab(
                isOpen = showStudio,
                onClick = { showStudio = !showStudio; if (showStudio) showSources = false }
            )
        }
    }
}
