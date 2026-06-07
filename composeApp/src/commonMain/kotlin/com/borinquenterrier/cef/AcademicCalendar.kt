package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@OptIn(ExperimentalFoundationApi::class)
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
    val routineRepository = remember { RoutineRepository(settings) }
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val authService = remember(settings) { GoogleAuthService(settings) }
    
    var routineEvents by remember { mutableStateOf(emptyList<TimeEvent>()) }
    var displayedEvents by remember { mutableStateOf(emptyList<Event>()) }
    var isGoogleLinked by remember { mutableStateOf(tokenRepository.hasTokens()) }
    var isSyncing by remember { mutableStateOf(false) }
    var selectedEventForDecomposition by remember { mutableStateOf<Event?>(null) }
    var activeSyncNegotiation by remember { mutableStateOf<SyncNegotiation?>(null) }
    val errorState by eventAgent.errorState.collectAsState()

    // Load the routine items
    LaunchedEffect(routineRepository) {
        routineEvents = routineRepository.getRoutineEvents()
    }

    // Initial Load and Sync
    LaunchedEffect(isGoogleLinked) {
        displayedEvents = calendarAgent.getEvents("default")
        if (isGoogleLinked) {
            scope.launch {
                isSyncing = true
                try {
                    val negotiation = calendarAgent.checkSyncProposals("default")
                    if (negotiation.proposals.isNotEmpty()) {
                        activeSyncNegotiation = negotiation
                    } else {
                        calendarAgent.applySyncNegotiation(negotiation, "default")
                        displayedEvents = calendarAgent.getEvents("default")
                    }
                } catch (e: Exception) {
                    // Sync failed, using cached local data
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    // Define the semester ranges
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val (viewStartDate, viewEndDate) = remember(today) {
        SemesterResolver.getSemesterRange(today)
    }

    val allExpandedEvents = remember(aiGeneratedEvents, routineEvents, displayedEvents, viewStartDate, viewEndDate) {
        EventDisplayPipeline.getExpandedAndFilteredEvents(
            routineEvents = routineEvents,
            aiGeneratedEvents = aiGeneratedEvents,
            displayedEvents = displayedEvents,
            startDate = viewStartDate,
            endDate = viewEndDate
        )
    }

    val groupedEvents = allExpandedEvents.groupBy { event ->
        when (event) {
            is TimeEvent -> event.date
            is DayEvent -> event.date
        }
    }

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
                    displayedEvents = calendarAgent.getEvents("default")
                }
            },
            onDismiss = {
                activeSyncNegotiation = null
            }
        )
    }

    Column(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Sticky error banner — shown at the top when a quota/AI error occurs
            stickyHeader {
                AnimatedErrorBanner(
                    error = errorState,
                    onDismiss = { eventAgent.clearError() },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (!isGoogleLinked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudCircle, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text("Sync with Google Calendar", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Link your account to import syllabi from Drive and push events to your Google Calendar.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        val result = authService.login()
                                        tokenRepository.saveTokens(result.first, result.second)
                                        isGoogleLinked = true
                                    } catch (e: Exception) {
                                    }
                                }
                            }) {
                                Text("Link Google Account")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onNavigate(AppScreen.Routine) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Weekly Routine")
                    }

                    Button(
                        onClick = { onNavigate(AppScreen.Home) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Add Source")
                    }

                    if (isGoogleLinked) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    try {
                                        val negotiation = calendarAgent.checkSyncProposals("default")
                                        if (negotiation.proposals.isNotEmpty()) {
                                            activeSyncNegotiation = negotiation
                                        } else {
                                            calendarAgent.applySyncNegotiation(negotiation, "default")
                                            displayedEvents = calendarAgent.getEvents("default")
                                        }
                                    } catch (e: Exception) {
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                        }
                    }
                }
            }

            if (groupedEvents.isEmpty()) {
                item {
                    Text(
                        text = "No events yet. Add a syllabus, calendar source, or weekly routine to get started.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                groupedEvents.forEach { (date, eventsOnDate) ->
                    stickyHeader {
                        Text(
                            text = date.toString(),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(eventsOnDate) { event ->
                        val isBreakable = event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS
                        EventItemView(
                            event = event,
                            onBreakItDown = if (isBreakable) { { selectedEventForDecomposition = event } } else null
                        )
                    }
                }
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
                Text("Due: ${event.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

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
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
fun EventItemView(event: Event, onBreakItDown: (() -> Unit)? = null) {
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
                Text(categoryLabel, style = MaterialTheme.typography.labelSmall, color = borderColor)
                if (event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS) {
                    val daysUntil = Clock.System.todayIn(TimeZone.currentSystemDefault()).daysUntil(event.date)
                    val chipText = remember(daysUntil) { EventPresenter.getDeadlineChipText(daysUntil) }
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
