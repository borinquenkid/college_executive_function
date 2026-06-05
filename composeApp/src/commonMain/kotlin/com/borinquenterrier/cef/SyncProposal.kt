package com.borinquenterrier.cef

sealed interface SyncProposal {
    val title: String
    val description: String

    data class StudyBlockShift(
        val originalEvent: Event,
        val proposedEvent: Event,
        val collidingEvent: Event
    ) : SyncProposal {
        override val title: String = "Shift Study Block"
        override val description: String
            get() {
                val originalTime = if (originalEvent is TimeEvent) "${originalEvent.startTime} - ${originalEvent.endTime}" else "All Day"
                val proposedTime = if (proposedEvent is TimeEvent) "${proposedEvent.startTime} - ${proposedEvent.endTime}" else "All Day"
                return "Move '${originalEvent.title}' on ${originalEvent.date} ($originalTime) to ${proposedEvent.date} ($proposedTime) because it conflicts with '${collidingEvent.title}'"
            }
    }

    data class DirectConflict(
        val localEvent: Event,
        val remoteEvent: Event
    ) : SyncProposal {
        override val title: String = "Conflicting Edits"
        override val description: String
            get() {
                val localDesc = if (localEvent is TimeEvent) "${localEvent.date} at ${localEvent.startTime}" else "${localEvent.date} (All Day)"
                val remoteDesc = if (remoteEvent is TimeEvent) "${remoteEvent.date} at ${remoteEvent.startTime}" else "${remoteEvent.date} (All Day)"
                return "'${localEvent.title}' has conflicting changes. Local: $localDesc. Remote: $remoteDesc. Remote version will overwrite local version."
            }
    }
}

data class SyncNegotiation(
    val proposals: List<SyncProposal>,
    val remoteEventsToSync: List<Event>,
    val deletedLocalIds: List<String>
) {
    val isEmpty: Boolean get() = proposals.isEmpty()
}
