@file:UiOnly
package com.borinquenterrier.cef

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Prominent dismissable banner shown when the Gemini API free-tier daily quota is exhausted.
 *
 * Displayed above the main content area in both [StudioPanel] and [AcademicCalendar].
 * The user can dismiss it; the [EventAgent.errorState] is cleared via [onDismiss].
 */
@Composable
fun QuotaErrorBanner(
    error: AgentError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, body) = when (error) {
        is AgentError.QuotaExhausted -> Pair(
            "Daily AI Quota Reached",
            "Your free-tier Gemini API key has hit its daily request limit. " +
                    "The quota resets at midnight Pacific Time. " +
                    "You can continue using the app manually — AI features will resume automatically tomorrow."
        )

        is AgentError.GenericError -> Pair(
            "AI Error",
            error.message.ifBlank { "An unexpected error occurred. Please try again." }
        )
    }

    val containerColor = when (error) {
        is AgentError.QuotaExhausted -> MaterialTheme.colorScheme.errorContainer
        is AgentError.GenericError -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Animated wrapper for [QuotaErrorBanner] — slides in from the top when [error] is non-null.
 */
@Composable
fun AnimatedErrorBanner(
    error: AgentError?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = error != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (error != null) {
            QuotaErrorBanner(
                error = error,
                onDismiss = onDismiss,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
