package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiEventsService {
    private val _aiGeneratedEvents = MutableStateFlow<List<Event>>(emptyList())
    val aiGeneratedEvents: StateFlow<List<Event>> = _aiGeneratedEvents.asStateFlow()

    fun addEvents(events: List<Event>) {
        _aiGeneratedEvents.value += events
    }

    fun clearEvents() {
        _aiGeneratedEvents.value = emptyList()
    }
}
