package com.borinquenterrier.cef

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Builds the "known deadlines from your calendar" block injected into the multi-source chat
 * prompt (tasks/plan.md T3/T4) — the deterministic deadline-safety channel.
 *
 * Rationale (owner's asymmetric cost model, 2026-08-09): a missed deadline is the failure an
 * executive-function-impaired student cannot self-correct, so date answers in chat must never
 * depend on lexical fragment retrieval finding the right paragraph. This digest guarantees the
 * guarantee-able part deterministically: **every event inside the near-term window is included
 * regardless of how the question is worded** (subject only to the hard caps, nearest-first).
 * Events beyond the window are included best-effort when their titles match the question.
 *
 * Events carrying a warning (e.g. ClassMeetingReconciler-inferred class meetings) render with a
 * ⚠ marker so inferred content stays visibly inferred all the way into the prompt — false
 * confidence corrodes the trust this population depends on.
 */
object EventsDigestBuilder {

    const val DEFAULT_MAX_LINES = 12
    const val DEFAULT_MAX_CHARS = 1_100
    /** Days ahead (inclusive) that are always represented, question-relevant or not. */
    const val NEAR_TERM_DAYS = 14

    fun build(
        events: List<Event>,
        question: String,
        today: LocalDate,
        maxLines: Int = DEFAULT_MAX_LINES,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): String? {
        if (events.isEmpty()) return null

        val sorted = events.sortedWith(compareBy({ it.date }, { it.title }))
        val windowEnd = today.plus(DatePeriod(days = NEAR_TERM_DAYS))

        // Near-term first (nearest-first priority under the caps), then question-matched others.
        val nearTerm = sorted.filter { it.date in today..windowEnd }
        val rest = sorted.filter { it.date !in today..windowEnd }
        val matchedRest = if (rest.isEmpty()) emptyList() else {
            Bm25Ranker.rank(question, rest.map { it.title })
                .filter { it.score > 0.0 }
                .map { rest[it.index] }
        }

        val selected = mutableListOf<Event>()
        var chars = 0
        for (event in nearTerm + matchedRest) {
            if (selected.size >= maxLines) break
            val line = lineFor(event)
            if (chars + line.length + 1 > maxChars && selected.isNotEmpty()) break
            selected += event
            chars += line.length + 1
        }
        if (selected.isEmpty()) return null

        // Chronological presentation regardless of selection priority.
        return selected.sortedWith(compareBy({ it.date }, { it.title }))
            .joinToString("\n") { lineFor(it) }
    }

    private fun lineFor(event: Event): String {
        val time = if (event is TimeEvent) " ${event.startTime}" else ""
        val marker = if (event.warning != null) " ⚠" else ""
        return "${event.date}$time | ${event.category.name} | ${event.title}$marker"
    }
}
