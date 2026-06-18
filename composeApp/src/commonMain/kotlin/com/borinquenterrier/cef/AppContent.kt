package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.datetime.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(container: DependencyContainer) {
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
                delay(3600_000L) // 1 hour
                try {
                    container.agentHarness.runHarness(force = false)
                } catch (e: Exception) {
                    println("[App] Periodic harness run failed: ${e.message}")
                }
            }
        }
    }

    val appController = container.appController
    val currentScreen by appController.currentScreen.asStateFlow().collectAsState()
    val aiGeneratedEvents by appController.aiGeneratedEvents.asStateFlow().collectAsState()

    val errorState by container.eventAgent.errorState.collectAsState()
    val incompleteEvents by container.eventAgent.incompleteEvents.collectAsState()
    var showCheckIn by remember { mutableStateOf(true) }

    if (showCheckIn && incompleteEvents.isNotEmpty()) {
        CheckInDialog(
            incompleteEvents = incompleteEvents,
            eventAgent = container.eventAgent,
            onDismiss = { showCheckIn = false }
        )
    }

    val globalHoldState by GeminiRetryService.globalHoldState.collectAsState()

    globalHoldState?.let { untilEpochMs ->
        var remainingSeconds by remember(untilEpochMs) {
            val now = Clock.System.now().toEpochMilliseconds()
            mutableStateOf(((untilEpochMs - now) + 999L) / 1000L)
        }

        LaunchedEffect(untilEpochMs) {
            while (true) {
                delay(500L)
                val now = Clock.System.now().toEpochMilliseconds()
                remainingSeconds = ((untilEpochMs - now) + 999L) / 1000L
                if (remainingSeconds <= 0) {
                    GeminiRetryService.clearGlobalHold()
                    break
                }
            }
        }

        if (remainingSeconds > 0) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("AI Key Saturated (Rate Limited)") },
                text = {
                    Column {
                        Text("All available Gemini models have hit their free-tier rate limits. To avoid request failures, the app is holding further requests.")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Resuming automatically in ${remainingSeconds.coerceAtLeast(0)} seconds...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { GeminiRetryService.cancelHold() }
                    ) {
                        Text("Cancel Wait")
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "College Executive Function",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = { appController.navigateTo(AppScreen.Home) },
                        modifier = Modifier.testTag("nav_home_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                    IconButton(
                        onClick = { appController.navigateTo(AppScreen.Calendar) },
                        modifier = Modifier.testTag("nav_calendar_button")
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Academic Calendar")
                    }
                    IconButton(
                        onClick = { appController.navigateTo(AppScreen.Settings) },
                        modifier = Modifier.testTag("nav_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedErrorBanner(
                error = errorState,
                onDismiss = { container.eventAgent.clearError() }
            )
            Box(Modifier.fillMaxSize()) {
            when (currentScreen) {
                is AppScreen.Home -> {
                    UniversalHomeLayout(container)
                }

                is AppScreen.Calendar -> {
                    AcademicCalendar(
                        modifier = Modifier.fillMaxSize(),
                        aiGeneratedEvents = aiGeneratedEvents,
                        calendarAgent = container.calendarAgent,
                        eventAgent = container.eventAgent,
                        onNavigate = { appController.navigateTo(it) }
                    )
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
