package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Pure deduplication pipeline for extracted calendar events.
 *
 * Three-step strategy (same as the original normalize() private methods):
 *  1. Same submission-canonical + date → prefer TimeEvent over DayEvent.
 *  2. Same date, 12-char common title prefix → keep the longer (more descriptive) title.
 *  3. Cross-date within 7 days for "submit/complete X" vs "X" → drop the earlier date.
 */
internal object EventDeduplicator {

    fun dedup(events: List<Event>): List<Event> {
        val preferTimed = events
            .groupBy { "${submissionCanonical(it.title)}-${dateOf(it)}" }
            .values
            .map { group -> group.maxByOrNull { if (it is TimeEvent) 1 else 0 }!! }

        val sameDateDeduped = dedupByCommonTitlePrefix(preferTimed)
        return dedupSubmissionPairs(sameDateDeduped)
    }

    internal fun dedupByCommonTitlePrefix(events: List<Event>): List<Event> {
        val toRemove = mutableSetOf<Event>()
        for (i in events.indices) {
            if (events[i] in toRemove) continue
            for (j in i + 1 until events.size) {
                if (events[j] in toRemove) continue
                val a = events[i]; val b = events[j]
                if (dateOf(a) != dateOf(b)) continue
                val aTitle = canonicalTitle(a.title)
                val bTitle = canonicalTitle(b.title)
                val prefixLen = commonPrefixLength(aTitle, bTitle)
                if (prefixLen >= 12) {
                    val discard = if (aTitle.length <= bTitle.length) a else b
                    toRemove.add(discard)
                }
            }
        }
        return events.filter { it !in toRemove }
    }

    internal fun dedupSubmissionPairs(events: List<Event>): List<Event> {
        val toRemove = mutableSetOf<Event>()
        val sorted = events.sortedBy { dateOf(it) }
        for (i in sorted.indices) {
            if (sorted[i] in toRemove) continue
            val a = sorted[i]
            val aCanon = submissionCanonical(a.title)
            val aDate = dateOf(a)
            for (j in i + 1 until sorted.size) {
                if (sorted[j] in toRemove) continue
                val b = sorted[j]
                val bDate = dateOf(b)
                val daysDiff = (bDate.toEpochDays() - aDate.toEpochDays()).toInt()
                if (daysDiff > 7) break
                val bCanon = submissionCanonical(b.title)
                val shorter = if (aCanon.length <= bCanon.length) aCanon else bCanon
                val longer = if (aCanon.length > bCanon.length) aCanon else bCanon
                val completePrefix = shorter.length >= 12 &&
                    longer.startsWith(shorter) &&
                    (longer.length == shorter.length || !longer[shorter.length].isLetterOrDigit())
                if (completePrefix) {
                    toRemove.add(a)
                }
            }
        }
        return sorted.filter { it !in toRemove }
    }

    internal fun canonicalTitle(title: String): String =
        title.trim().lowercase()
            .replace(Regex("^your\\s+"), "")

    internal fun submissionCanonical(title: String): String =
        canonicalTitle(title)
            .replace(Regex("\\s+"), " ").trim()                                    // collapse internal whitespace
            .replace(Regex("#(\\d)"), "$1")                                        // "#1" → "1"
            .replace(Regex("\\bdeadline\\b"), "due")                               // synonym
            .replace(Regex("\\bdue\\s+date\\b"), "due")                            // "due date" → "due"
            .trimEnd('.')                                                            // trailing period
            .replace(Regex("^(submit|complete|upload|post|turn in|hand in|finish|do)\\s+"), "")
            .replace(Regex("^the\\s+"), "")                                        // article after verb
            .replace(Regex("^your\\s+"), "")

    internal fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        for (i in 0 until limit) { if (a[i] != b[i]) return i }
        return limit
    }

    fun dateOf(event: Event): LocalDate = when (event) {
        is DayEvent -> event.date
        is TimeEvent -> event.date
    }
}
