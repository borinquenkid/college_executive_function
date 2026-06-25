package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

class CalendarAgent(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null,
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp
) {
    private val _resetVersion = MutableStateFlow(0)
    val resetVersion: StateFlow<Int> = _resetVersion.asStateFlow()

    private val syncGate = SyncGate(localRepo)
    private val persistence = RemoteFirstEventPersistence(
        localRepo, remoteRepo, syncGate, logger, userPreferenceMemoryRepository
    )
    private val negotiator =
        SyncNegotiator(localRepo, remoteRepo, userPreferenceMemoryRepository, preferencesRepository)
    private val negotiationApplier =
        SyncNegotiationApplier(localRepo, remoteRepo, logger, userPreferenceMemoryRepository)

    suspend fun getEvents(calendarId: String = "default"): List<Event> =
        localRepo.getAllEvents(calendarId)

    suspend fun saveEvent(event: Event, calendarId: String = "default") {
        val repaired = EventTimeRepairer.repair(event).also { it.validate() }
        persistence.save(repaired, calendarId)
    }

    suspend fun updateEvent(event: Event, calendarId: String = "default") {
        val repaired = EventTimeRepairer.repair(event).also { it.validate() }
        persistence.update(repaired, calendarId)
    }

    suspend fun saveEventLocally(event: Event, calendarId: String = "default") {
        val repaired = EventTimeRepairer.repair(event).also { it.validate() }
        localRepo.saveEvent(repaired.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
    }

    suspend fun hardDeleteLocalOnly(id: String, calendarId: String) =
        localRepo.hardDeleteEvent(id, calendarId)

    suspend fun retryLocalOnly(calendarId: String = "default") =
        persistence.retryLocalOnly(calendarId)

    suspend fun deleteEvent(eventId: String, calendarId: String = "default") =
        persistence.delete(eventId, calendarId)

    suspend fun resetCalendar(calendarId: String = "default") {
        persistence.reset(calendarId)
        _resetVersion.value++
    }

    suspend fun checkSyncProposals(calendarId: String = "default"): SyncNegotiation =
        negotiator.buildNegotiation(calendarId)

    suspend fun applySyncNegotiation(negotiation: SyncNegotiation, calendarId: String = "default") =
        negotiationApplier.apply(negotiation, calendarId)

    suspend fun synchronize(calendarId: String = "default") {
        applySyncNegotiation(checkSyncProposals(calendarId), calendarId)
    }

    suspend fun getIncompleteEventsBefore(
        date: LocalDate,
        calendarId: String = "default"
    ): List<Event> = localRepo.getIncompleteEventsBefore(date, calendarId)
}
