package com.borinquenterrier.cef

import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

class CalendarAgent(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp,
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp,
    // Backoff between rate-limited remote deletes during a reset; tests inject a no-op for speed.
    private val remoteClearDelayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {
    private val _resetVersion = MutableStateFlow(0)
    val resetVersion: StateFlow<Int> = _resetVersion.asStateFlow()

    private val syncGate = SyncGate(localRepo)
    private val persistence = RemoteFirstEventPersistence(
        localRepo, remoteRepo, syncGate, logger, userPreferenceMemoryRepository, remoteClearDelayFn
    )
    private val negotiator =
        SyncNegotiator(localRepo, remoteRepo, userPreferenceMemoryRepository, preferencesRepository)
    private val negotiationApplier =
        SyncNegotiationApplier(localRepo, remoteRepo, logger, userPreferenceMemoryRepository)

    suspend fun getEvents(calendarId: String = "default"): List<Event> =
        localRepo.getAllEvents(calendarId)

    /**
     * Events restricted to the active semester window (from preferences) for display.
     * Remote-synced STUDENT events bypass ingestion's semester filter, so the calendar view
     * applies it here; reconcile/sync/delete paths deliberately keep using [getEvents] (the
     * full set) so out-of-window events are never silently dropped or re-pushed.
     */
    suspend fun getSemesterEvents(calendarId: String = "default"): List<Event> =
        SemesterFilter.apply(getEvents(calendarId), preferencesRepository.getPreferences())

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
        // Self-heal: after reconciling local↔remote, auto-correct the SAFE drift and surface the
        // rest. Wrapped so a heal failure never breaks the sync itself.
        try {
            selfHeal(calendarId)
        } catch (e: Exception) {
            logger?.e("CalendarAgent", "Self-heal after sync failed (non-fatal): ${e.message}", e)
        }
    }

    private val _pendingOutOfSemester = MutableStateFlow<List<Event>>(emptyList())

    /** Out-of-term events found by the last self-heal — surfaced for user review (never auto-deleted). */
    val pendingOutOfSemester: StateFlow<List<Event>> = _pendingOutOfSemester.asStateFlow()

    /**
     * Auto-corrects the drift that is SAFE to fix without asking (exact duplicates, updatedAt=0),
     * and records out-of-term events in [pendingOutOfSemester] for the user to confirm — deleting a
     * real out-of-term event is a judgement call, not a silent action. Returns the full report.
     */
    suspend fun selfHeal(calendarId: String = "default"): ReconciliationReport {
        val report = reconcile(calendarId)
        // Apply only duplicates + timestamp stamps; leave out-of-term for review.
        applyReconciliation(report.copy(outOfSemesterToDelete = emptyList()), calendarId)
        _pendingOutOfSemester.value = report.outOfSemesterToDelete
        return report
    }

    /**
     * Read-only integrity check (e.g. at startup): records out-of-term drift in
     * [pendingOutOfSemester] so the UI can badge the Repair action, WITHOUT changing anything.
     */
    suspend fun checkHealth(calendarId: String = "default"): ReconciliationReport {
        val report = reconcile(calendarId)
        _pendingOutOfSemester.value = report.outOfSemesterToDelete
        return report
    }

    suspend fun getIncompleteEventsBefore(
        date: LocalDate,
        calendarId: String = "default"
    ): List<Event> = localRepo.getIncompleteEventsBefore(date, calendarId)

    /**
     * Read-only health check: detects calendar drift the happy path can't (exact duplicates,
     * out-of-term events, and the updatedAt=0 events that cause endless sync churn). Nothing is
     * changed — the caller shows the [ReconciliationReport] and calls [applyReconciliation] to fix.
     */
    suspend fun reconcile(calendarId: String = "default"): ReconciliationReport =
        CalendarReconciler.analyze(
            localRepo.getAllEvents(calendarId).filter { it.syncStatus != SyncStatus.DELETED_LOCALLY },
            preferencesRepository.getPreferences()
        )

    /**
     * Applies the fixes in [report]: deletes duplicate + out-of-term copies from BOTH local and
     * remote, and stamps a real timestamp on updatedAt=0 events so sync stops overwriting them.
     */
    suspend fun applyReconciliation(report: ReconciliationReport, calendarId: String = "default") {
        (report.duplicatesToDelete + report.outOfSemesterToDelete).forEach { event ->
            event.id?.let { persistence.delete(it, calendarId) }
        }
        val now = Clock.System.now().toEpochMilliseconds()
        report.timestampsToStamp.forEach { event ->
            persistence.update(event.withUpdatedAt(now), calendarId)
        }
        _resetVersion.value++
    }
}
