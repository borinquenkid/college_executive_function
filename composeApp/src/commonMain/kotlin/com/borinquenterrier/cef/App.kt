package com.borinquenterrier.cef

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                TopAppBar(
                    title = { Text("College Executive Function") },
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
            val modifier = Modifier.fillMaxSize().padding(paddingValues)

            when (currentScreen) {
                is AppScreen.Home -> {
                    if (isDesktop) {
                        DesktopApp(
                            modifier, 
                            appController
                        )
                    } else {
                        MobileApp(
                            modifier, 
                            appController
                        )
                    }
                }
                is AppScreen.Calendar -> {
                    AcademicCalendar(modifier, aiGeneratedEvents, container.calendarAgent) { appController.navigateTo(it) }
                }
                is AppScreen.Settings -> {
                    SettingsScreen(container, modifier)
                }
                is AppScreen.Routine -> {
                    RoutineScreen(modifier)
                }
            }
        }
    }
}

@Composable
fun DesktopApp(
    modifier: Modifier = Modifier, 
    appController: AppController
) {
    val container = appController.container
    val sourceItems by appController.sourceItems.collectAsState()
    val selectedSource by appController.selectedSource.collectAsState()

    var showSources by remember { mutableStateOf(true) }
    var showStudio by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    val sourceProviders = remember(container) {
        listOf(
            LocalFileSourceProvider(container.ingestionAgent, container.aiService),
            UrlSourceProvider(container.ingestionAgent, container.aiService),
            GoogleDriveSourceProvider(container.ingestionAgent, container.driveService, container.tokenRepository)
        )
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = showSources, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourcesPanel(
                    modifier = Modifier.weight(1f),
                    sourceItems = sourceItems,
                    selectedSource = selectedSource,
                    onSourceSelected = { appController.selectSource(it) },
                    onSourceAdded = { source ->
                        appController.addSource(source)
                        coroutineScope.launch {
                            val allEvents = if (container.aiService.isConfigured()) {
                                container.aiService.generateCalendarEvents(source.fragments)
                            } else {
                                emptyList()
                            }
                            appController.addEvents(allEvents)
                        }
                    },
                    providers = sourceProviders
                )
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

        ChatPanel(modifier = Modifier.weight(2f), selectedSource = selectedSource)

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
                StudioPanel(
                    modifier = Modifier.weight(1f), 
                    selectedSource = selectedSource, 
                    calendarAgent = container.calendarAgent, 
                    container = container,
                    onEventsGenerated = { appController.addEvents(it) }
                )
            }
        }
    }
}

@Composable
fun MobileApp(
    modifier: Modifier = Modifier, 
    appController: AppController
) {
    val container = appController.container
    val sourceItems by appController.sourceItems.collectAsState()
    val selectedSource by appController.selectedSource.collectAsState()

    var showSources by remember { mutableStateOf(sourceItems.isEmpty()) }
    var showStudio by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val sourceProviders = remember(container) {
        listOf(
            LocalFileSourceProvider(container.ingestionAgent, container.aiService),
            UrlSourceProvider(container.ingestionAgent, container.aiService),
            GoogleDriveSourceProvider(container.ingestionAgent, container.driveService, container.tokenRepository)
        )
    }

    Column(modifier = modifier) {
        // Sources Panel (Top)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = {
                val newShowSources = !showSources
                if (newShowSources) {
                    showStudio = false
                }
                showSources = newShowSources
            }) {
                Icon(
                    imageVector = if (showSources) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (showSources) "Hide Sources" else "Show Sources"
                )
            }
            AnimatedVisibility(visible = showSources) {
                SourcesPanel(
                    modifier = Modifier.fillMaxWidth(),
                    sourceItems = sourceItems,
                    selectedSource = selectedSource,
                    onSourceSelected = { appController.selectSource(it) },
                    onSourceAdded = { source ->
                        appController.addSource(source)
                        coroutineScope.launch {
                            val allEvents = if (container.aiService.isConfigured()) {
                                container.aiService.generateCalendarEvents(source.fragments)
                            } else {
                                emptyList()
                            }
                            appController.addEvents(allEvents)
                        }
                    },
                    providers = sourceProviders
                )
            }
        }

        // Chat Panel (Middle)
        ChatPanel(modifier = Modifier.weight(1f), selectedSource = selectedSource)

        // Studio Panel (Bottom)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = showStudio) {
                StudioPanel(
                    modifier = Modifier.fillMaxWidth(), 
                    selectedSource = selectedSource, 
                    calendarAgent = container.calendarAgent, 
                    container = container,
                    onEventsGenerated = { appController.addEvents(it) }
                )
            }
            IconButton(onClick = {
                val newShowStudio = !showStudio
                if (newShowStudio) {
                    showSources = false
                }
                showStudio = newShowStudio
            }) {
                Icon(
                    imageVector = if (showStudio) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (showStudio) "Hide Studio" else "Show Studio"
                )
            }
        }
    }
}
