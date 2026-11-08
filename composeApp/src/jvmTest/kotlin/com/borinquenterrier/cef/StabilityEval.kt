package com.borinquenterrier.cef

import kotlinx.serialization.Serializable

/** One extracted event reduced to what stability compares: its date and a punctuation/case-insensitive title. */
data class StabilityEvent(val date: String, val title: String)

/** Per-document consistency across repeated extractions of the same file (T5, the pass^k idea). */
@Serializable
data class DocumentStability(
    val runs: Int,
    val failedRuns: Int,
    /** Every successful run produced the same set of dates. Meaningless when fewer than 2 runs succeeded. */
    val dateSetStable: Boolean,
    /** Stricter: same (date, normalized title) pairs on every successful run. Title wording drift breaks this. */
    val eventSetStable: Boolean,
    val distinctDateSets: Int,
    val minEvents: Int,
    val maxEvents: Int,
    /** Dates that appeared in some successful runs but not all. */
    val unstableDates: List<String> = emptyList()
) {
    val comparable: Boolean get() = runs - failedRuns >= 2
}

@Serializable
data class StabilityEvalMetrics(
    val runsPerDocument: Int,
    val perDocument: Map<String, DocumentStability>,
    val modelUsed: String? = null
) {
    val comparableDocuments: Int get() = perDocument.values.count { it.comparable }
    val dateStableDocuments: Int get() = perDocument.values.count { it.comparable && it.dateSetStable }
    val eventStableDocuments: Int get() = perDocument.values.count { it.comparable && it.eventSetStable }
}

object StabilityScorer {
    private fun normalize(title: String) =
        title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** [runs] holds one entry per attempted run; a null entry is a run that failed (timeout, 5xx). */
    fun score(runs: List<List<StabilityEvent>?>): DocumentStability {
        val ok = runs.filterNotNull()
        val dateSets = ok.map { run -> run.map { it.date }.toSet() }
        val eventSets = ok.map { run -> run.map { it.date to normalize(it.title) }.toSet() }
        val everyDate = dateSets.flatten().toSet()
        val unstable = if (dateSets.size < 2) emptyList()
        else everyDate.filter { d -> dateSets.any { d !in it } }.sorted()
        return DocumentStability(
            runs = runs.size,
            failedRuns = runs.size - ok.size,
            dateSetStable = dateSets.size >= 2 && dateSets.distinct().size == 1,
            eventSetStable = eventSets.size >= 2 && eventSets.distinct().size == 1,
            distinctDateSets = dateSets.distinct().size,
            minEvents = ok.minOfOrNull { it.size } ?: 0,
            maxEvents = ok.maxOfOrNull { it.size } ?: 0,
            unstableDates = unstable
        )
    }
}
