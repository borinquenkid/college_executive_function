package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicCalendar(
    modifier: Modifier = Modifier,
    aiGeneratedEvents: List<Event>,
    calendarAgent: CalendarAgent,
    eventAgent: EventAgent,
    onNavigate: (AppScreen) -> Unit,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
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
                    today = today,
                    onEventSelected = { selectedEventForDecomposition = it }
                )
            }
        }
    }
}

@Composable
fun TaskDecompositionDialog(event: Event, eventAgent: EventAgent, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val decomposedTasks by eventAgent.decomposedTasks.collectAsState()
    val isLoading by eventAgent.isLoading.collectAsState()
    val statusMessage by eventAgent.statusMessage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Break It Down", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Due: ${event.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (decomposedTasks.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    if (isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
                        }
                    } else {
                        Button(
                            onClick = { scope.launch { eventAgent.decomposeTask(event) } },
                            modifier = Modifier.fillMaxWidth().testTag("break_it_down_button")
                        ) {
                            Text("Break It Down (AI)")
                        }
                    }
                } else {
                    HorizontalDivider()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 160.dp)
                    ) {
                        items(decomposedTasks) { task ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        "${task.daysBeforeDue} day${if (task.daysBeforeDue != 1) "s" else ""} before due",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(task.title, style = MaterialTheme.typography.bodyMedium)
                                    if (task.description.isNotBlank()) {
                                        Text(
                                            task.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = { scope.launch { eventAgent.acceptDecomposition(); onDismiss() } },
                        modifier = Modifier.fillMaxWidth().testTag("add_steps_button"),
                        enabled = !isLoading
                    ) {
                        Text("Add Steps to Calendar")
                    }
                }

                if (statusMessage.isNotBlank() && statusMessage != "Select a source and an action.") {
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EventItemView(
    event: Event,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    onBreakItDown: (() -> Unit)? = null
) {
    val borderColor = remember(event.category, event.source) {
        EventPresenter.getEventBorderColor(event.category, event.source)
    }

    val categoryLabel = remember(event.category, event.source) {
        EventPresenter.getCategoryLabel(event.category, event.source)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(2.dp, borderColor, CardDefaults.shape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = borderColor
                )
                if (event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS) {
                    val daysUntil = today.daysUntil(event.date)
                    val chipText =
                        remember(daysUntil) { EventPresenter.getDeadlineChipText(daysUntil) }
                    val status = remember(daysUntil) { EventPresenter.getDeadlineStatus(daysUntil) }
                    val chipColor = when (status) {
                        EventPresenter.DeadlineStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
                        EventPresenter.DeadlineStatus.DUE_TODAY -> MaterialTheme.colorScheme.tertiaryContainer
                        EventPresenter.DeadlineStatus.FUTURE -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    val chipTextColor = when (status) {
                        EventPresenter.DeadlineStatus.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
                        EventPresenter.DeadlineStatus.DUE_TODAY -> MaterialTheme.colorScheme.onTertiaryContainer
                        EventPresenter.DeadlineStatus.FUTURE -> MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Surface(
                        color = chipColor,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            color = chipTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            when (event) {
                is TimeEvent -> {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text("From ${event.startTime} to ${event.endTime}")
                }

                is DayEvent -> {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text("All day")
                }
            }
            if (event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS) {
                val progress = event.studyProgress()
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Study Progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            if (onBreakItDown != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBreakItDown,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Break It Down (AI)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
