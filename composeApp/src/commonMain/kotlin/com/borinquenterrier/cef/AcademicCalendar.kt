package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@Composable
fun AcademicCalendar(modifier: Modifier = Modifier, events: List<CalendarEvent>) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val currentMonth = today.month
    val currentYear = today.year
    val firstDayOfMonth = today.replace(dayOfMonth = 1)
    val startDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + 1
    val daysInMonth = currentMonth.length(isLeapYear(currentYear))

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Text(
            text = "${currentMonth.name} $currentYear",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        // Days of the week header
        Row(modifier = Modifier.fillMaxWidth()) {
            val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            for (day in days) {
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize()
        ) {
            // Add empty cells for padding before the first day of the month
            items(startDayOfWeek - 1) { Spacer(Modifier) }

            items(daysInMonth) { day ->
                val date = today.replace(dayOfMonth = day + 1)
                Column(modifier = Modifier.padding(4.dp)) {
                    Text((day + 1).toString())
                    events.filter { it.startTime.toLocalDate() == date }.forEach { event ->
                        Text(event.title, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// Helper to check for leap year
private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

// Extension function to get the length of a month
private fun kotlinx.datetime.Month.length(isLeap: Boolean): Int {
    return when (this) {
        kotlinx.datetime.Month.FEBRUARY -> if (isLeap) 29 else 28
        kotlinx.datetime.Month.APRIL, kotlinx.datetime.Month.JUNE, kotlinx.datetime.Month.SEPTEMBER, kotlinx.datetime.Month.NOVEMBER -> 30
        else -> 31
    }
}

private fun kotlinx.datetime.LocalDate.replace(dayOfMonth: Int) = kotlinx.datetime.LocalDate(this.year, this.month, dayOfMonth)
private fun kotlinx.datetime.Instant.toLocalDate() = this.toLocalDateTime(TimeZone.currentSystemDefault()).date
