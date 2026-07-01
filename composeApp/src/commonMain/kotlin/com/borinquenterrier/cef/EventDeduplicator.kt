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

    // AcademicCategory names an LLM sometimes leaks into a title, e.g. "DEADLINE: Issue Brief #2"
    // or "STUDY_BLOCK: Drafting". "Class Session:" is NOT one of these (CLASS needs a bare colon),
    // so descriptive titles that merely start with a word are preserved.
    private val LEAKED_CATEGORY_PREFIX = Regex(
        "^\\s*(regular|holiday|deadline|finals|semester_bound|study_block|class)\\s*:\\s*",
        RegexOption.IGNORE_CASE
    )

    /** Removes an LLM-leaked category label like "DEADLINE: " from the start of a display title. */
    fun stripLeakedCategoryPrefix(title: String): String =
        LEAKED_CATEGORY_PREFIX.replace(title, "").trim()

    fun dedup(events: List<Event>): List<Event> {
        val preferTimed = events
            .groupBy { "${submissionCanonical(it.title)}-${dateOf(it)}" }
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy({ if (it is TimeEvent) 1 else 0 }, { it.title.length })
                )!!
            }

        val sameDateDeduped = dedupByCommonTitlePrefix(preferTimed)
        val deliverableDeduped = dedupSameDateDeliverable(sameDateDeduped)
        return dedupSubmissionPairs(deliverableDeduped)
    }

    /**
     * Same-date only: collapse events whose deliverable canonical is equal, or where one is a
     * complete whole-word leading phrase of the other (≥2 words) — catches "Final Paper" vs
     * "Final Paper: Hidden Systems …". Same-date guard keeps genuinely distinct deliverables on
     * different days (e.g. the Final Paper Planning Guide) untouched. Keeps the longer title.
     */
    internal fun dedupSameDateDeliverable(events: List<Event>): List<Event> {
        val toRemove = mutableSetOf<Event>()
        for ((_, group) in events.groupBy { dateOf(it) }) {
            for (i in group.indices) {
                if (group[i] in toRemove) continue
                for (j in i + 1 until group.size) {
                    if (group[j] in toRemove) continue
                    val a = group[i]; val b = group[j]
                    val ca = submissionCanonical(a.title)
                    val cb = submissionCanonical(b.title)
                    if (ca == cb || isCompleteWordPrefix(ca, cb) || isCompleteWordPrefix(cb, ca)) {
                        toRemove.add(if (a.title.length <= b.title.length) a else b)
                    }
                }
            }
        }
        return events.filter { it !in toRemove }
    }

    /**
     * True when [short] is a subtitle-boundary leading phrase of [long] (≥2 words), i.e. the
     * character after the prefix is a colon: "final paper" ⊂ "final paper: hidden systems …".
     * A space boundary is deliberately NOT enough — "issue brief 2" is a space-prefix of the
     * distinct "issue brief 2 drafting" (a study block, not the deadline).
     */
    internal fun isCompleteWordPrefix(short: String, long: String): Boolean {
        if (short.isEmpty() || short.length >= long.length) return false
        if (!long.startsWith(short)) return false
        if (short.split(" ").size < 2) return false
        return long[short.length] == ':'
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
                // Cross-date: only fold an identical deliverable, or a subtitle ("X" vs "X: sub").
                // A space boundary would wrongly fold "issue brief 2" into "issue brief 2 drafting"
                // (a study block on a different day), so it is intentionally excluded here.
                val completePrefix = shorter.length >= 12 &&
                    longer.startsWith(shorter) &&
                    (longer.length == shorter.length || longer[shorter.length] == ':')
                if (completePrefix) {
                    toRemove.add(a)
                }
            }
        }
        return sorted.filter { it !in toRemove }
    }

    internal fun canonicalTitle(title: String): String =
        stripLeakedCategoryPrefix(title).lowercase()                              // drop leaked "DEADLINE:" etc.
            .replace(Regex("^your\\s+"), "")

    internal fun submissionCanonical(title: String): String =
        canonicalTitle(title)
            .replace(Regex("\\s+"), " ").trim()                                    // collapse internal whitespace
            .replace(Regex("#(\\d)"), "$1")                                        // "#1" → "1"
            .replace(Regex("\\s*\\([^)]*\\)"), "")                                 // drop qualifiers "(final draft)"
            .replace(Regex("\\bdeadline\\b"), "due")                               // synonym
            .replace(Regex("\\bdue\\s+date\\b"), "due")                            // "due date" → "due"
            .trimEnd('.')                                                            // trailing period
            .replace(Regex("^(submit|complete|upload|post|turn in|hand in|finish|do)\\s+"), "")
            .replace(Regex("^the\\s+"), "")                                        // article after verb
            .replace(Regex("^your\\s+"), "")
            .replace(Regex("\\s+(due|deadline)$"), "")                             // trailing "… due"
            .trim()

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
