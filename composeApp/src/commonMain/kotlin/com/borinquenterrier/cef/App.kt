package com.borinquenterrier.cef

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.borinquenterrier.cef.ui.theme.CollegeExecutiveFunctionTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    val settings = rememberSettings()
    val logger = rememberLogger()
    val driverFactory = rememberDriverFactory()
    val modelBasePath = rememberModelDirectoryPath()
    val fileReader = rememberLocalFileReader()
    val docxReader = rememberDocxReader()
    val pdfReader = rememberPdfReader()

    val container = remember(settings, logger, driverFactory, modelBasePath, fileReader, docxReader, pdfReader) {
        DependencyContainer(settings, logger, driverFactory, modelBasePath, fileReader, docxReader, pdfReader)
    }
    
    val appController = container.appController

    CollegeExecutiveFunctionTheme {
        val currentScreen by appController.currentScreen.collectAsState()
        val aiGeneratedEvents by appController.aiGeneratedEvents.collectAsState()
        
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("College Executive Function", style = MaterialTheme.typography.titleMedium) },
                    actions = {
                        IconButton(onClick = { appController.navigateTo(AppScreen.Home) }) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                        IconButton(onClick = { appController.navigateTo(AppScreen.Calendar) }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Academic Calendar")
                        }
                        IconButton(onClick = { appController.navigateTo(AppScreen.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                when (currentScreen) {
                    is AppScreen.Home -> {
                        UniversalHomeLayout(container)
                    }
                    is AppScreen.Calendar -> {
                        AcademicCalendar(Modifier.fillMaxSize(), aiGeneratedEvents, container.calendarAgent) { appController.navigateTo(it) }
                    }
                    is AppScreen.Settings -> {
                        SettingsScreen(container, Modifier.fillMaxSize())
                    }
                    is AppScreen.Routine -> {
                        RoutineScreen(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun UniversalHomeLayout(container: DependencyContainer) {
    val appController = container.appController
    val sourceItems by appController.sourceItems.collectAsState()
    val selectedSource by appController.selectedSource.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showSources by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }

    val sourceProviders = remember(container) {
        listOf(
            LocalFileSourceProvider(container.ingestionAgent, container.aiService),
            UrlSourceProvider(container.ingestionAgent, container.aiService),
            GoogleDriveSourceProvider(container.ingestionAgent, container.driveService, container.tokenRepository)
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        
        // --- BASE LAYER: THE PERMANENT CHAT CANVAS ---
        // This is always the foundation. It fills the screen and is never squished.
        ChatPanel(
            modifier = Modifier.fillMaxSize(),
            appController = appController
        )

        // --- LAYER 2: EDGE NAVIGATION (THE FLOATING CONTROLS) ---
        // Left Edge: Sources Trigger
        Box(Modifier.fillMaxHeight().align(Alignment.CenterStart).padding(start = 8.dp), contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = { showSources = !showSources; if(showSources) showStudio = false },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(if (showSources) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.Default.Menu, "Sources")
            }
        }

        // Right Edge: Studio Trigger
        Box(Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(end = 8.dp), contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = { showStudio = !showStudio; if(showStudio) showSources = false },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(if (showStudio) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.Build, "Studio")
            }
        }

        // --- LAYER 3: ADAPTIVE OVERLAY DRAWERS ---
        // Sources Overlay (Left)
        androidx.compose.animation.AnimatedVisibility(
            visible = showSources,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().width(320.dp).shadow(16.dp),
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
                            coroutineScope.launch {
                                if (container.aiService.isConfigured()) {
                                    val allEvents = container.aiService.generateCalendarEvents(source.fragments)
                                    appController.addEvents(allEvents)
                                    container.contextAgent.analyzeSource(source)
                                }
                            }
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
                modifier = Modifier.fillMaxHeight().width(360.dp).shadow(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box {
                    StudioPanel(
                        selectedSource = selectedSource,
                        calendarAgent = container.calendarAgent,
                        container = container,
                        onEventsGenerated = { appController.addEvents(it) }
                    )
                    // Close shortcut
                    IconButton(
                        onClick = { showStudio = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) { Icon(Icons.Default.Close, null) }
                }
            }
        }
    }
}
