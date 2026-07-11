package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Detects and processes term boundaries for cross-term memory (ADR 0004 / ROADMAP Phase 13,
 * XM-3). Uses the existing pure [SemesterResolver.getSemesterRange] applied to event dates — NOT
 * [WarningClassifier.activeSemesterFrom], which resolves from wall-clock `today` for its own
 * UI-warning purpose and is the wrong primitive for a data-driven batch trigger (see ADR 0004's
 * Alternatives Considered).
 */
object TermBoundaryTrigger {

    /**
     * Every distinct semester range present in [events], excluding the range containing the
     * newest event (that term may still be ongoing/incomplete) and any range already present in
     * [alreadyRecordedTermStarts]. A term only ever gets flagged once its own end has been
     * superseded by newer data.
     */
    fun detectNewlyCompletedTerms(
        events: List<Event>,
        alreadyRecordedTermStarts: Set<LocalDate>
    ): List<Pair<LocalDate, LocalDate>> {
        if (events.isEmpty()) return emptyList()

        val maxDate = events.maxOf { it.date }
        val ongoingTerm = SemesterResolver.getSemesterRange(maxDate)

        return events.map { SemesterResolver.getSemesterRange(it.date) }
            .distinct()
            .filter { it != ongoingTerm }
            .filter { it.first !in alreadyRecordedTermStarts }
    }
}

/**
 * Runs [TermBoundaryTrigger.detectNewlyCompletedTerms] against [repository]'s already-recorded
 * terms, then aggregates and saves exactly one [StudentTermProfile] row per newly-completed term
 * — a single batched write per term boundary crossed, never one write per event. Idempotent:
 * re-running with the same [events]/[repository] state detects nothing new (the term is already
 * in [alreadyRecordedTermStarts]) and [TermProfileRepository.save]'s upsert is itself idempotent
 * if it were ever called twice for the same term.
 */
suspend fun processNewlyCompletedTerms(events: List<Event>, repository: TermProfileRepository) {
    val recordedTermStarts = repository.getAll().map { it.termStart }.toSet()
    val newTerms = TermBoundaryTrigger.detectNewlyCompletedTerms(events, recordedTermStarts)

    newTerms.forEach { (start, end) ->
        val termEvents = events.filter { it.date >= start && it.date <= end }
        TermProfileAggregator.aggregate(termEvents)?.let { repository.save(it) }
    }
}
