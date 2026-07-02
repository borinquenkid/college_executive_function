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
    // Source list for orphan detection during reconcile; null disables it.
    private val sourceRepository: SourceRepository? = null,
    // Backoff between rate-limited remote deletes during a reset; tests inject a no-op for speed.
    private val remoteClearDelayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {
    private val _resetVersion = MutableStateFlow(0)
    val resetVersion: StateFlow<Int> = _resetVersion.asStateFlow()

    // True while a reset is clearing local+remote. Sync is suppressed during this window so a
    // concurrent background sync (harness poll, push, source-delete) can't re-pull the SHRINKING
    // remote back into the just-emptied local — which made reset appear to fail / show events
    // disappearing one by one.
    private val isResetting = MutableStateFlow(false)

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
        // Clear local AND remote fully before signalling the UI, and suppress sync meanwhile
        // (see isResetting) so nothing re-pulls the not-yet-cleared remote back into local — the
        // reset must actually stick, not reappear.
        isResetting.value = true
        try {
            persistence.reset(calendarId)
        } finally {
            isResetting.value = false
            _resetVersion.value++
        }
    }

    suspend fun checkSyncProposals(calendarId: String = "default"): SyncNegotiation {
        if (isResetting.value) return SyncNegotiation(emptyList(), emptyList(), emptyList())
        return negotiator.buildNegotiation(calendarId)
    }

    suspend fun applySyncNegotiation(negotiation: SyncNegotiation, calendarId: String = "default") =
        negotiationApplier.apply(negotiation, calendarId)

    suspend fun synchronize(calendarId: String = "default") {
        if (isResetting.value) return
        applySyncNegotiation(checkSyncProposals(calendarId), calendarId)
        // Self-heal: after reconciling local↔remote, auto-correct the SAFE drift and surface the
        // rest. Wrapped so a heal failure never breaks the sync itself.
        try {
            selfHeal(calendarId)
        } catch (e: Exception) {
            logger?.e("CalendarAgent", "Self-heal after sync failed (non-fatal): ${e.message}", e)
        }
    }

    private val _pendingReview = MutableStateFlow<List<Event>>(emptyList())

    /** Deletions that need user review (out-of-term + orphaned) — surfaced, never auto-deleted. */
    val pendingReview: StateFlow<List<Event>> = _pendingReview.asStateFlow()

    /**
     * Auto-corrects the drift that is SAFE to fix without asking (exact duplicates, updatedAt=0),
     * and records the deletions that are a judgement call (out-of-term, orphaned) in [pendingReview]
     * for the user to confirm — never deleting a real event silently. Returns the full report.
     */
    suspend fun selfHeal(calendarId: String = "default"): ReconciliationReport =
        AppTracer.current.span("calendar.self_heal", mapOf("calendar.id" to calendarId)) {
            val report = reconcile(calendarId)
            setAttribute("drift.duplicates", report.duplicatesToDelete.size.toLong())
            setAttribute("drift.out_of_term", report.outOfSemesterToDelete.size.toLong())
            setAttribute("drift.orphans", report.orphansToDelete.size.toLong())
            setAttribute("drift.stale_timestamps", report.timestampsToStamp.size.toLong())
            // Apply only duplicates + timestamp stamps; leave out-of-term + orphans for review.
            applyReconciliation(
                report.copy(outOfSemesterToDelete = emptyList(), orphansToDelete = emptyList()),
                calendarId
            )
            _pendingReview.value = report.outOfSemesterToDelete + report.orphansToDelete
            setAttribute("review.pending_count", _pendingReview.value.size.toLong())
            report
        }

    /**
     * Read-only integrity check (e.g. at startup): records review-needed drift in [pendingReview]
     * so the UI can badge the Repair action, WITHOUT changing anything.
     */
    suspend fun checkHealth(calendarId: String = "default"): ReconciliationReport {
        val report = reconcile(calendarId)
        _pendingReview.value = report.outOfSemesterToDelete + report.orphansToDelete
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
            preferencesRepository.getPreferences(),
            knownSourceIds = sourceRepository?.getAllSources()?.mapTo(HashSet()) { it.id }
        )

    /**
     * Applies the fixes in [report]: deletes duplicate + out-of-term + orphaned copies from BOTH
     * local and remote, and stamps a real timestamp on updatedAt=0 events so sync stops overwriting.
     */
    suspend fun applyReconciliation(report: ReconciliationReport, calendarId: String = "default") {
        AppTracer.current.span("calendar.reconcile_apply") {
            val toDelete = report.duplicatesToDelete + report.outOfSemesterToDelete + report.orphansToDelete
            toDelete.forEach { event -> event.id?.let { persistence.delete(it, calendarId) } }
            val now = Clock.System.now().toEpochMilliseconds()
            report.timestampsToStamp.forEach { event ->
                persistence.update(event.withUpdatedAt(now), calendarId)
            }
            setAttribute("reconcile.deleted", toDelete.size.toLong())
            setAttribute("reconcile.stamped", report.timestampsToStamp.size.toLong())
            // Only refresh the UI when something actually changed, so a no-op self-heal on every
            // periodic sync doesn't keep re-rendering the calendar mid-ingest.
            if (toDelete.isNotEmpty() || report.timestampsToStamp.isNotEmpty()) {
                _resetVersion.value++
            }
        }
    }
}
