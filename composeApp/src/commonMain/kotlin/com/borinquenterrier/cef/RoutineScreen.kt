package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun RoutineScreen(modifier: Modifier = Modifier) {
    val settings = rememberSettings()
    val repository = remember { RoutineRepository(settings) }
    val scope = rememberCoroutineScope()
    var routineEvents by remember { mutableStateOf(emptyList<TimeEvent>()) }
    var showAddDialog by remember { mutableStateOf(value = false) }

    LaunchedEffect(Unit) {
        routineEvents = repository.getRoutineEvents()
    }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Weekly Routine", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Define your recurring weekly schedule, like classes, meals, or gym time.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Routine Item")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routineEvents) { event ->
                RoutineEventView(event)
            }
        }
    }

    if (showAddDialog) {
        AddRoutineItemDialog(
            onDismiss = { showAddDialog = false }
        ) { newEvent ->
            scope.launch {
                repository.saveRoutineEvents(routineEvents + newEvent)
            }
            routineEvents = routineEvents + newEvent
        }
    }
}

@Composable
fun RoutineEventView(event: TimeEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            event.recurrence?.let {
                val days = it.daysOfWeek.joinToString(", ") { day -> day.name.take(3) }
                Text("Repeats on $days from ${event.startTime} to ${event.endTime}")
                Text("(${it.startDate} to ${it.endDate})")
            }
        }
    }
}
