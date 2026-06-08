package com.borinquenterrier.cef

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Initialize the container off the main thread to prevent ANRs
    val containerState = produceState<DependencyContainer?>(
        initialValue = null,
        settings, logger, driverFactory, modelBasePath, fileReader, docxReader, pdfReader
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val c = DependencyContainer(settings, logger, driverFactory, modelBasePath, fileReader, docxReader, pdfReader)
            // Pre-trigger database initialization to ensure it happens off-thread
            val db = c.database 
            println("[App] Core services initialized off-thread.")
            c
        }
    }

    val container = containerState.value

    CollegeExecutiveFunctionTheme {
        if (container == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LaunchedEffect(container) {
                // Startup check
                launch {
                    try {
                        container.agentHarness.runHarness(force = false)
                    } catch (e: Exception) {
                        println("[App] Harness startup run failed: ${e.message}")
                    }
                }
                // Periodic daily check (polling hourly)
                launch {
                    while (true) {
                        kotlinx.coroutines.delay(3600_000L) // 1 hour
                        try {
                            container.agentHarness.runHarness(force = false)
                        } catch (e: Exception) {
                            println("[App] Periodic harness run failed: ${e.message}")
                        }
                    }
                }
            }

            val appController = container.appController
            val currentScreen by appController.currentScreen.collectAsState()
            val aiGeneratedEvents by appController.aiGeneratedEvents.collectAsState()

            val incompleteEvents by container.eventAgent.incompleteEvents.collectAsState()
            var showCheckIn by remember { mutableStateOf(true) }

            if (showCheckIn && incompleteEvents.isNotEmpty()) {
                CheckInDialog(
                    incompleteEvents = incompleteEvents,
                    eventAgent = container.eventAgent,
                    onDismiss = { showCheckIn = false }
                )
            }
            
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
                            AcademicCalendar(Modifier.fillMaxSize(), aiGeneratedEvents, container.calendarAgent, container.eventAgent) { appController.navigateTo(it) }
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
}
