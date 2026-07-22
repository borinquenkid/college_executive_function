@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicCalendar(
    modifier: Modifier = Modifier,
    aiGeneratedEvents: List<Event>,
    calendarAgent: CalendarAgent,
    eventAgent: EventAgent,
    authService: GoogleAuthService,
    onNavigate: (AppScreen) -> Unit,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val logger = rememberLogger()
    val routineRepository = remember { RoutineRepository(settings) }
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }

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
    var isSyncing by remember { mutableStateOf(value = false) }
    var selectedEventForDecomposition by remember { mutableStateOf<Event?>(null) }
    var activeSyncNegotiation by remember { mutableStateOf<SyncNegotiation?>(null) }
    val resetVersion by calendarAgent.resetVersion.collectAsState()

    LaunchedEffect(routineRepository) {
        routineEvents = routineRepository.getRoutineEvents()
    }

    LaunchedEffect(isGoogleLinked, resetVersion) {
        displayedEvents = calendarAgent.getSemesterEvents("default")
        if (isGoogleLinked) {
            scope.launch {
                isSyncing = true
                try {
                    performCalendarSync(
                        initiateSync = { syncManager.initiateSyncIfNeeded(it) },
                        refreshEvents = { syncManager.refreshEvents() },
                        forceSync = isGoogleLinked,
                        onNegotiation = { activeSyncNegotiation = it }
                    ) {
                        displayedEvents = it
                    }
                } catch (e: Exception) {
                    logger.e("AcademicCalendar", "Initial calendar sync failed", e)
                    // Auth errors already handled by GoogleTokenService.onAuthExpired;
                    // other transient errors are silently dropped so the app stays alive.
                } finally {
                    if (isSyncing) isSyncing = false
                }
            }
        }
    }

    val currentSemesterEvents = remember(displayedEvents, today) {
        SemesterResolver.filterToActiveSemester(displayedEvents, today)
    }

    val (viewStartDate, viewEndDate) = remember(today, aiGeneratedEvents, currentSemesterEvents) {
        SemesterResolver.getExpandedRange(today, aiGeneratedEvents + currentSemesterEvents)
    }

    val allExpandedEvents =
        remember(aiGeneratedEvents, routineEvents, currentSemesterEvents, viewStartDate, viewEndDate) {
            EventDisplayPipeline.getExpandedAndFilteredEvents(
                routineEvents = routineEvents,
                aiGeneratedEvents = aiGeneratedEvents,
                displayedEvents = currentSemesterEvents,
                startDate = viewStartDate,
                endDate = viewEndDate
            )
        }

    val groupedEvents = CalendarEventGrouper.groupEventsByDate(allExpandedEvents)

    DecompositionDialogFor(
        event = selectedEventForDecomposition,
        eventAgent = eventAgent
    ) {
        eventAgent.clearDecomposition()
        if (selectedEventForDecomposition != null) {
            selectedEventForDecomposition = null
        }
        // Study steps just added by "Add Steps to Calendar" are saved straight through
        // CalendarAgent.saveEvent, which never bumps resetVersion — so without this, the
        // already-mounted Calendar screen keeps showing its stale pre-decomposition event list
        // until the user leaves and re-enters the screen (or does something else that happens
        // to bump resetVersion).
        scope.launch {
            displayedEvents = calendarAgent.getSemesterEvents("default")
        }
    }

    SyncNegotiationDialogFor(
        negotiation = activeSyncNegotiation,
        calendarAgent = calendarAgent,
        onApplied = {
            if (activeSyncNegotiation != null) {
                activeSyncNegotiation = null
            }
            scope.launch {
                try {
                    displayedEvents = syncManager.refreshEvents()
                } catch (e: Exception) {
                    logger.e("AcademicCalendar", "Refresh after sync negotiation applied failed", e)
                    // Auth errors already handled by GoogleTokenService.onAuthExpired.
                }
            }
        }
    ) {
        if (activeSyncNegotiation != null) {
            activeSyncNegotiation = null
        }
    }


    Column(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (!isGoogleLinked) {
                item {
                    GoogleLinkPrompt(
                        onLink = { authManager.loginAndLink() }
                    ) {
                        isGoogleLinked = true
                    }
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
                                performCalendarSync(
                                    initiateSync = { syncManager.initiateSyncIfNeeded(it) },
                                    refreshEvents = { syncManager.refreshEvents() },
                                    forceSync = true,
                                    onNegotiation = { activeSyncNegotiation = it },
                                ) {
                                    displayedEvents = it
                                }
                            } catch (e: Exception) {
                                logger.e("AcademicCalendar", "Manual calendar sync failed", e)
                                // Auth errors already handled by GoogleTokenService.onAuthExpired.
                            } finally {
                                if (isSyncing) isSyncing = false
                            }
                        }
                    },
                    onExport = {
                        scope.launch {
                            try {
                                val icsContent = generateIcsString(displayedEvents)
                                val filePath = writeIcsFile(icsContent)
                                eventAgent.updateStatus("Exported calendar: $filePath")
                            } catch (e: Exception) {
                                logger.e("AcademicCalendar", "ICS export failed", e)
                                eventAgent.updateStatus("Export failed: ${e.message}")
                            }
                        }
                    }
                )
            }

            item {
                EventListContent(
                    groupedEvents = groupedEvents,
                    today = today,
                    onEventSelected = { selectedEventForDecomposition = it },
                )
            }
        }
    }
}

