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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borinquenterrier.cef.ui.theme.CollegeExecutiveFunctionTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.borinquenterrier.cef.db.createDatabase
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.flow.collect

sealed class Screen {
    object Home : Screen()
    object Calendar : Screen()
    object Settings : Screen()
    object Routine : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    CollegeExecutiveFunctionTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var aiGeneratedEvents by remember { mutableStateOf(listOf<Event>()) }

        val settings = rememberSettings()
        val logger = rememberLogger()
        val modelBasePath = rememberModelDirectoryPath()
        val httpClient = remember { 
            HttpClient {
                install(ContentNegotiation) {
                    json(kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    })
                }
            } 
        }
        val modelManager = remember(httpClient, modelBasePath, logger) { 
            ModelManager(httpClient, modelBasePath, logger) 
        }
        
        var isDownloadingModel by remember { mutableStateOf(!modelManager.isModelDownloaded()) }
        
        if (isDownloadingModel) {
            LaunchedEffect(Unit) {
                modelManager.downloadModel().collect { progress ->
                    if (progress.isDone) {
                        isDownloadingModel = false
                    }
                }
            }
            
            Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Downloading AI Model...", color = Color.White)
                        Text("This may take a few minutes (9B params, ~5.6GB)", color = Color.White)
                    }
                }
            }
        }

        val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
        val authService = remember(settings) { GoogleAuthService(settings) }
        val driverFactory = rememberDriverFactory()
        val database = remember(driverFactory) { createDatabase(driverFactory) }
        val localRepository = remember(database) { SqlDelightLocalCalendarRepository(database) }
        val syncService = remember { 
            GoogleCalendarSyncService(HttpClient {
                install(ContentNegotiation) {
                    json(kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    })
                }
            }) 
        }
        val remoteRepository = remember(syncService, tokenRepository, authService) {
            GoogleRemoteCalendarRepository(syncService, tokenRepository, authService)
        }
        val unifiedRepository = remember(localRepository, remoteRepository) {
            UnifiedCalendarRepository(localRepository, remoteRepository)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("College Executive Function") },
                    actions = {
                        IconButton(onClick = { currentScreen = Screen.Home }) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                        IconButton(onClick = { currentScreen = Screen.Calendar }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Academic Calendar")
                        }
                        IconButton(onClick = { currentScreen = Screen.Settings }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            val modifier = Modifier.fillMaxSize().padding(paddingValues)
            val driveService = remember { 
                GoogleDriveService(
                    HttpClient { install(ContentNegotiation) { json() } },
                    tokenRepository,
                    authService
                ) 
            }

            when (currentScreen) {
                is Screen.Home -> {
                    if (isDesktop) {
                        DesktopApp(
                            modifier, 
                            unifiedRepository, 
                            tokenRepository,
                            authService,
                            driveService
                        ) { aiGeneratedEvents = aiGeneratedEvents + it }
                    } else {
                        MobileApp(
                            modifier, 
                            unifiedRepository,
                            tokenRepository,
                            authService,
                            driveService
                        ) { aiGeneratedEvents = aiGeneratedEvents + it }
                    }
                }
                is Screen.Calendar -> {
                    AcademicCalendar(modifier, aiGeneratedEvents, unifiedRepository) { currentScreen = it }
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        tokenRepository,
                        authService,
                        driveService,
                        modifier
                    )
                }
                is Screen.Routine -> {
                    RoutineScreen(modifier)
                }
            }
        }
    }
}

@Composable
fun DesktopApp(
    modifier: Modifier = Modifier, 
    unifiedRepository: UnifiedCalendarRepository, 
    tokenRepository: GoogleTokenRepository,
    authService: GoogleAuthService,
    driveService: GoogleDriveService,
    onEventsGenerated: (List<Event>) -> Unit
) {
    var showSources by remember { mutableStateOf(true) }
    var showStudio by remember { mutableStateOf(true) }
    var sourceItems by remember { mutableStateOf(emptyList<SourceItem>()) }
    var selectedSource by remember { mutableStateOf<SourceItem?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val aiService = rememberAIService()
    
    val webReader = remember { WebSourceReader() }
    val fileReader = rememberLocalFileReader()
    val docxReader = rememberDocxReader()
    val pdfReader = rememberPdfReader()

    val sourceProviders = remember(tokenRepository, aiService, driveService) {
        listOf(
            LocalFileSourceProvider(fileReader, docxReader, pdfReader, aiService),
            UrlSourceProvider(webReader, aiService),
            GoogleDriveSourceProvider(driveService, tokenRepository)
        )
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = showSources, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourcesPanel(
                    modifier = Modifier.weight(1f),
                    sourceItems = sourceItems,
                    selectedSource = selectedSource,
                    onSourceSelected = { selectedSource = it },
                    onSourceAdded = { source ->
                        sourceItems = sourceItems + source
                        coroutineScope.launch {
                            val allEvents = if (aiService.isConfigured()) {
                                aiService.generateCalendarEvents(source.parts)
                            } else {
                                emptyList()
                            }
                            onEventsGenerated(allEvents)
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
                StudioPanel(modifier = Modifier.weight(1f), selectedSource = selectedSource, unifiedRepository = unifiedRepository, onEventsGenerated = onEventsGenerated)
            }
        }
    }
}

@Composable
fun MobileApp(
    modifier: Modifier = Modifier, 
    unifiedRepository: UnifiedCalendarRepository, 
    tokenRepository: GoogleTokenRepository,
    authService: GoogleAuthService,
    driveService: GoogleDriveService,
    onEventsGenerated: (List<Event>) -> Unit
) {
    var showSources by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }
    var sourceItems by remember { mutableStateOf(emptyList<SourceItem>()) }
    var selectedSource by remember { mutableStateOf<SourceItem?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val aiService = rememberAIService()
    
    val webReader = remember { WebSourceReader() }
    val fileReader = rememberLocalFileReader()
    val docxReader = rememberDocxReader()
    val pdfReader = rememberPdfReader()

    val sourceProviders = remember(tokenRepository, aiService, driveService) {
        listOf(
            LocalFileSourceProvider(fileReader, docxReader, pdfReader, aiService),
            UrlSourceProvider(webReader, aiService),
            GoogleDriveSourceProvider(driveService, tokenRepository)
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
                    onSourceSelected = { selectedSource = it },
                    onSourceAdded = { source ->
                        sourceItems = sourceItems + source
                        coroutineScope.launch {
                            val allEvents = if (aiService.isConfigured()) {
                                aiService.generateCalendarEvents(source.parts)
                            } else {
                                emptyList()
                            }
                            onEventsGenerated(allEvents)
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
                StudioPanel(modifier = Modifier.fillMaxWidth(), selectedSource = selectedSource, unifiedRepository = unifiedRepository, onEventsGenerated = onEventsGenerated)
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
