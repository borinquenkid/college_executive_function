package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

@Composable
internal fun DecompositionDialogFor(
    event: Event?,
    eventAgent: EventAgent,
    onDismiss: () -> Unit
) {
    event ?: return
    TaskDecompositionDialog(event = event, eventAgent = eventAgent, onDismiss = onDismiss)
}

@Composable
internal fun SyncNegotiationDialogFor(
    negotiation: SyncNegotiation?,
    calendarAgent: CalendarAgent,
    onApplied: () -> Unit,
    onDismiss: () -> Unit
) {
    negotiation ?: return
    SyncNegotiationDialog(
        negotiation = negotiation,
        calendarAgent = calendarAgent,
        onApplied = onApplied,
        onDismiss = onDismiss
    )
}
