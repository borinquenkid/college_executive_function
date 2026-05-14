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
                    calendarAgent.synchronize("default")
                    displayedEvents = calendarAgent.getEvents("default")
                } catch (e: Exception) {
                    // Sync failed, using cached local data
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    // Define the semester ranges
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val currentYear = today.year
    
    val isFirstSemester = today.monthNumber in 8..12
    val isSecondSemester = today.monthNumber in 1..5
    
    val (viewStartDate, viewEndDate) = when {
        isFirstSemester -> {
            LocalDate(currentYear, 8, 1) to LocalDate(currentYear, 12, 31)
        }
        isSecondSemester -> {
            LocalDate(currentYear, 1, 1) to LocalDate(currentYear, 5, 31)
        }
        else -> {
            today to today.plus(30, DateTimeUnit.DAY)
        }
    }

    val allExpandedEvents = remember(aiGeneratedEvents, routineEvents, displayedEvents, viewStartDate, viewEndDate) {
        val expandedRoutineEvents = EventGenerator.expandEvents(routineEvents, viewStartDate, viewEndDate)
        val expandedAiEvents = EventGenerator.expandEvents(aiGeneratedEvents, viewStartDate, viewEndDate)
        
        (expandedRoutineEvents + expandedAiEvents + displayedEvents)
            .filter { event ->
                val date = when (event) {
                    is TimeEvent -> event.date
                    is DayEvent -> event.date
                }
                date in viewStartDate..viewEndDate
            }
            .sortedBy { event ->
                when (event) {
                    is TimeEvent -> event.date.atStartOfDayIn(TimeZone.currentSystemDefault())
                    is DayEvent -> event.date.atStartOfDayIn(TimeZone.currentSystemDefault())
                }
            }
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

    Column(modifier = modifier) {
        if (!isGoogleLinked) {
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

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                calendarAgent.synchronize("default")
                                displayedEvents = calendarAgent.getEvents("default")
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

        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text("Due: ${event.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                if (decomposedTasks.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    if (isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Button(
                            onClick = { scope.launch { eventAgent.decomposeTask(event) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Break It Down (AI)")
                        }
                    }
                } else {
                    HorizontalDivider()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        items(decomposedTasks) { task ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
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
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { scope.launch { eventAgent.acceptDecomposition(); onDismiss() } },
                        modifier = Modifier.fillMaxWidth(),
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
    val borderColor = when (event.category) {
        AcademicCategory.HOLIDAY -> Color(0xFFFF5252) // Red-ish for Holidays
        AcademicCategory.DEADLINE -> Color(0xFFFF9800) // Orange for Deadlines
        AcademicCategory.FINALS -> Color(0xFF9C27B0) // Purple for Finals
        AcademicCategory.SEMESTER_BOUND -> Color(0xFF607D8B) // Grey for Start/End
        AcademicCategory.STUDY_BLOCK -> Color(0xFF8BC34A) // Light Green for Study
        AcademicCategory.CLASS -> Color(0xFF3F51B5) // Indigo for Class
        AcademicCategory.REGULAR -> when (event.source) {
            EventSource.ROUTINE -> Color(0xFF4CAF50) // Green
            EventSource.AI_GENERATED -> Color(0xFF2196F3) // Blue
            EventSource.MANUAL -> Color(0xFFBDBDBD) // Light Grey
            EventSource.STUDENT -> Color(0xFFE91E63) // Pink
            EventSource.SCHOOL -> Color(0xFF9E9E9E) // Grey
            EventSource.CLASS -> Color(0xFF3F51B5) // Indigo
        }
    }
    
    val categoryLabel = when (event.category) {
        AcademicCategory.HOLIDAY -> "Holiday/Break"
        AcademicCategory.DEADLINE -> "Important Deadline"
        AcademicCategory.FINALS -> "Finals Week"
        AcademicCategory.SEMESTER_BOUND -> "Semester Boundary"
        AcademicCategory.STUDY_BLOCK -> "Suggested Study Period"
        AcademicCategory.CLASS -> "Scheduled Class"
        AcademicCategory.REGULAR -> when (event.source) {
            EventSource.ROUTINE -> "Class/Routine"
            EventSource.AI_GENERATED -> "Homework/Assignment"
            EventSource.MANUAL -> "School Calendar"
            EventSource.STUDENT -> "Personal"
            EventSource.SCHOOL -> "Institutional"
            EventSource.CLASS -> "Course Item"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(2.dp, borderColor, CardDefaults.shape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(categoryLabel, style = MaterialTheme.typography.labelSmall, color = borderColor)
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
