package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

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
            .border(2.dp, borderColor, CardDefaults.shape),
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
                if ((event.category == AcademicCategory.DEADLINE) || (event.category == AcademicCategory.FINALS)) {
                    val daysUntil =
                        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.daysUntil(event.date)
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
            if ((event.category == AcademicCategory.DEADLINE) || (event.category == AcademicCategory.FINALS)) {
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
                        progress = { progress },
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
