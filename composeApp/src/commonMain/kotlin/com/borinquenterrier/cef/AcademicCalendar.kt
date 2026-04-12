package com.borinquenterrier.cef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicCalendar(modifier: Modifier = Modifier, aiGeneratedEvents: List<Event>, onNavigate: (Screen) -> Unit) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val repository = remember { RoutineRepository(settings) }
    val tokenRepository = remember(settings) { GoogleTokenRepository(settings) }
    val authService = remember(settings) { GoogleAuthService(settings) }
    
    var routineEvents by remember { mutableStateOf(emptyList<TimeEvent>()) }
    var googleEvents by remember { mutableStateOf(emptyList<Event>()) }
    var isGoogleLinked by remember { mutableStateOf(tokenRepository.hasTokens()) }
    
    val syncService = remember { GoogleCalendarSyncService(HttpClient {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }) }
    
    val calendarRepository = remember(syncService, tokenRepository) {
        GoogleRemoteCalendarRepository(syncService, tokenRepository)
    }

    // Load the routine items
    LaunchedEffect(repository) {
        routineEvents = repository.getRoutineEvents()
    }

    // Fetch Google events if linked
    LaunchedEffect(isGoogleLinked) {
        if (isGoogleLinked) {
            try {
                // Fetch from the 'primary' calendar for now
                googleEvents = calendarRepository.getAllEvents("primary")
            } catch (e: Exception) {
                // Handle fetch error
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
            // Summer or fallback: Show a one-month view from today
            today to today.plus(30, DateTimeUnit.DAY)
        }
    }

    val allExpandedEvents = remember(aiGeneratedEvents, routineEvents, googleEvents, viewStartDate, viewEndDate) {
        val expandedRoutineEvents = EventGenerator.expandEvents(routineEvents, viewStartDate, viewEndDate)
        val expandedAiEvents = EventGenerator.expandEvents(aiGeneratedEvents, viewStartDate, viewEndDate)
        
        // Filter out any events that fall outside the current semester's window
        (expandedRoutineEvents + expandedAiEvents + googleEvents)
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
                                val access = result.first
                                val refresh = result.second
                                tokenRepository.saveTokens(access, refresh)
                                isGoogleLinked = true
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    }) {
                        Text("Link Google Account")
                    }
                }
            }
        }

        Button(
            onClick = { onNavigate(Screen.Routine) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Manage Weekly Routine")
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
                        EventItemView(event)
                    }
                }
            }
        }
    }
}

@Composable
fun EventItemView(event: Event) {
    val borderColor = when (event.category) {
        AcademicCategory.HOLIDAY -> Color(0xFFFF5252) // Red-ish for Holidays
        AcademicCategory.DEADLINE -> Color(0xFFFF9800) // Orange for Deadlines
        AcademicCategory.FINALS -> Color(0xFF9C27B0) // Purple for Finals
        AcademicCategory.SEMESTER_BOUND -> Color(0xFF607D8B) // Grey for Start/End
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
        }
    }
}
