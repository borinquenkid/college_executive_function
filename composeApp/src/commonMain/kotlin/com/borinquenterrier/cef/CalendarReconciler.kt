package com.borinquenterrier.cef

/**
 * A read-only assessment of calendar drift. Each list names the concrete events an [apply] step
 * would act on, so the UI can show exactly what will change before anything is touched.
 */
data class ReconciliationReport(
    /** Redundant copies to delete (the best copy in each duplicate group is kept, not listed here). */
    val duplicatesToDelete: List<Event> = emptyList(),
    /** Events whose date falls outside the configured semester window. */
    val outOfSemesterToDelete: List<Event> = emptyList(),
    /** In-good-standing events carrying updatedAt=0, which makes sync perpetually overwrite them. */
    val timestampsToStamp: List<Event> = emptyList()
) {
    val isClean: Boolean
        get() = duplicatesToDelete.isEmpty() && outOfSemesterToDelete.isEmpty() && timestampsToStamp.isEmpty()

    val totalIssues: Int
        get() = duplicatesToDelete.size + outOfSemesterToDelete.size + timestampsToStamp.size

    fun summary(): String = if (isClean) {
        "Calendar is healthy — no issues found."
    } else {
        buildList {
            if (duplicatesToDelete.isNotEmpty()) add("${duplicatesToDelete.size} duplicate(s)")
            if (outOfSemesterToDelete.isNotEmpty()) add("${outOfSemesterToDelete.size} out-of-term")
            if (timestampsToStamp.isNotEmpty()) add("${timestampsToStamp.size} stale timestamp(s)")
        }.joinToString(", ")
    }
}

/**
 * Detects calendar drift the happy path can't: exact duplicates, out-of-term events, and the
 * updatedAt=0 events that cause the endless "remote overrides local" sync churn. Pure and
 * side-effect free — it only decides WHAT is wrong; [CalendarAgent] applies the fixes.
 *
 * Deliberately conservative about deletion (this is a real calendar): a "duplicate" is only an
 * EXACT match — same submission-canonical title AND same date. Near-duplicate collapsing already
 * happens at generation via [EventDeduplicator]; here we only remove copies that are unambiguous.
 */
object CalendarReconciler {

    fun analyze(events: List<Event>, preferences: StudyPreferences): ReconciliationReport {
        val window = SemesterFilter.window(preferences)

        val outOfSemester = if (window == null) emptyList() else
            events.filter { it.date < window.first || it.date > window.second }
        val outIds = outOfSemester.mapTo(HashSet()) { it }

        // Only consider in-term events for duplicate/timestamp checks — out-of-term ones are
        // already slated for deletion.
        val inTerm = events.filter { it !in outIds }

        val duplicatesToDelete = inTerm
            .groupBy { EventDeduplicator.submissionCanonical(it.title) to it.date }
            .values
            .filter { it.size > 1 }
            .flatMap { group -> group.sortedWith(keepPreference).drop(1) } // keep the best, delete rest

        val survivors = inTerm.filter { it !in duplicatesToDelete.toHashSet() }
        val timestampsToStamp = survivors.filter { it.updatedAt == 0L }

        return ReconciliationReport(duplicatesToDelete, outOfSemester, timestampsToStamp)
    }

    // Which copy of a duplicate to KEEP (sorts best-first): a synced copy over local-only, a timed
    // copy over all-day, one with a real id, then the more descriptive (longer) title.
    private val keepPreference: Comparator<Event> = compareByDescending<Event> {
        if (it.syncStatus == SyncStatus.SYNCED) 1 else 0
    }.thenByDescending { if (it is TimeEvent) 1 else 0 }
        .thenByDescending { if (it.id != null) 1 else 0 }
        .thenByDescending { it.title.length }
}
