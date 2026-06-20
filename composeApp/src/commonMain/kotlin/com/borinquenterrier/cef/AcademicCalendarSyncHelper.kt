package com.borinquenterrier.cef

internal suspend fun performCalendarSync(
    initiateSync: suspend (Boolean) -> SyncNegotiation?,
    refreshEvents: suspend () -> List<Event>,
    forceSync: Boolean,
    onNegotiation: (SyncNegotiation) -> Unit,
    onEventsRefreshed: (List<Event>) -> Unit
) {
    val negotiation = initiateSync(forceSync)
    if (negotiation != null) {
        onNegotiation(negotiation)
    } else {
        onEventsRefreshed(refreshEvents())
    }
}
