package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@OptIn(ExperimentalFoundationApi::class, kotlin.time.ExperimentalTime::class)
@Composable
fun AcademicCalendar(
    modifier: Modifier = Modifier,
    aiGeneratedEvents: List<Event>,
    calendarAgent: CalendarAgent,
    eventAgent: EventAgent,
    onNavigate: (AppScreen) -> Unit
) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val logger = rememberLogger()
    val routineRepository = remember { RoutineRepository(settings) }
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val authService = remember(settings) { GoogleAuthService(settings) }

    // Delegate business logic to extracted services
    val authManager = remember(authService, tokenRepository, logger) {
        GoogleAuthManager(authService, tokenRepository, logger)
    }
    val syncManager = remember(calendarAgent, logger) {
        CalendarSyncManager(calendarAgent, logger)
    }

    var routineEvents by remember { mutableStateOf(emptyList<TimeEvent>()) }
    var displayedEvents by remember { mutableStateOf(emptyList<Event>()) }
    var isGoogleLinked by remember { mutableStateOf(authManager.isLinked()) }
    var isSyncing by remember { mutableStateOf(false) }
    var selectedEventForDecomposition by remember { mutableStateOf<Event?>(null) }
    var activeSyncNegotiation by remember { mutableStateOf<SyncNegotiation?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    val errorState by eventAgent.errorState.collectAsState()
    val unresolvedConflicts by eventAgent.unresolvedConflicts.collectAsState()

    LaunchedEffect(routineRepository) {
        routineEvents = routineRepository.getRoutineEvents()
    }

    LaunchedEffect(isGoogleLinked) {
        displayedEvents = calendarAgent.getEvents("default")
        if (isGoogleLinked) {
            scope.launch {
                isSyncing = true
                try {
                    val negotiation = syncManager.initiateSyncIfNeeded(isGoogleLinked)
                    if (negotiation != null) {
                        activeSyncNegotiation = negotiation
                    } else {
                        displayedEvents = syncManager.refreshEvents()
                    }
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    LaunchedEffect(unresolvedConflicts) {
        if (unresolvedConflicts.isNotEmpty()) {
            showConflictDialog = true
        }
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val (viewStartDate, viewEndDate) = remember(today, aiGeneratedEvents, displayedEvents) {
        SemesterResolver.getExpandedRange(today, aiGeneratedEvents + displayedEvents)
    }

    val allExpandedEvents =
        remember(aiGeneratedEvents, routineEvents, displayedEvents, viewStartDate, viewEndDate) {
            EventDisplayPipeline.getExpandedAndFilteredEvents(
                routineEvents = routineEvents,
                aiGeneratedEvents = aiGeneratedEvents,
                displayedEvents = displayedEvents,
                startDate = viewStartDate,
                endDate = viewEndDate
            )
        }

    val groupedEvents = CalendarEventGrouper.groupEventsByDate(allExpandedEvents)

    selectedEventForDecomposition?.let { event ->
        TaskDecompositionDialog(
            event = event,
            eventAgent = eventAgent,
            onDismiss = {
                eventAgent.clearDecomposition()
                selectedEventForDecomposition = null
            }
        )
    }

    activeSyncNegotiation?.let { negotiation ->
        SyncNegotiationDialog(
            negotiation = negotiation,
            calendarAgent = calendarAgent,
            onApplied = {
                activeSyncNegotiation = null
                scope.launch {
                    displayedEvents = syncManager.refreshEvents()
                }
            },
            onDismiss = {
                activeSyncNegotiation = null
            }
        )
    }

    if (showConflictDialog) {
        ConflictResolutionDialog(
            conflicts = unresolvedConflicts,
            onDismiss = {
                showConflictDialog = false
                eventAgent.clearUnresolvedConflicts()
            }
        )
    }

    Column(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            stickyHeader {
                AnimatedErrorBanner(
                    error = errorState,
                    onDismiss = { eventAgent.clearError() },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (!isGoogleLinked) {
                item {
                    GoogleLinkPrompt(
                        onLink = { authManager.loginAndLink() },
                        onLinked = { isGoogleLinked = true }
                    )
                }
            }

            item {
                AcademicCalendarHeader(
                    isGoogleLinked = isGoogleLinked,
                    isSyncing = isSyncing,
                    onNavigateRoutine = { onNavigate(AppScreen.Routine) },
                    onNavigateHome = { onNavigate(AppScreen.Home) },
                    onSync = {
                        scope.launch {
                            isSyncing = true
                            try {
                                val negotiation = syncManager.initiateSyncIfNeeded(true)
                                if (negotiation != null) {
                                    activeSyncNegotiation = negotiation
                                } else {
                                    displayedEvents = syncManager.refreshEvents()
                                }
                            } finally {
                                isSyncing = false
                            }
                        }
                    }
                )
            }

            item {
                EventListContent(
                    groupedEvents = groupedEvents,
                    onEventSelected = { selectedEventForDecomposition = it }
                )
            }
        }
    }
}
