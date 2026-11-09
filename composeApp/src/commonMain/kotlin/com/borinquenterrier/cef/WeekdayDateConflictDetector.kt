package com.borinquenterrier.cef

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod

/**
 * Finds dates in a source whose weekday contradicts the calendar (e.g. "Friday, Nov. 21" when Nov 21 is a
 * Saturday) and holds the matching extracted events back for the student to decide.
 *
 * Owner rule (Walter, 2026-11-08): a date the instructor got wrong must NOT be guessed, either way. Extraction
 * auto-pushes with no confirm step (ADR 0015), so a guessed date would land on the calendar as fact. This is a
 * deterministic check on purpose: the extraction prompt already asks the model to warn on contradictory text, and
 * it never did.
 *
 * Year handling: the source often names no year, so the year is the one whose calendar agrees with the most
 * weekday+date mentions in the document. If that is not clear (fewer than [MIN_AGREEING] agreeing mentions, under
 * [MIN_AGREEMENT_RATIO] agreement, or a tie) the detector stays silent rather than risk false conflicts.
 */
object WeekdayDateConflictDetector {

    internal const val MIN_AGREEING = 3
    internal const val MIN_AGREEMENT_RATIO = 0.75
    private const val MIN_TITLE_COVERAGE = 0.6

    /** One "weekday + month/day" mention found in the source, with the line it sits on. */
    internal data class Mention(val text: String, val weekday: DayOfWeek, val month: Int, val day: Int, val line: String)

    /** A mention whose weekday contradicts the document's year. [candidates] are the two dates a reader might mean. */
    internal data class Conflict(val mention: Mention, val year: Int, val candidates: Set<LocalDate>)

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val WEEKDAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY
    )

    private val MENTION = Regex(
        """\b(Mon(?:day)?|Tue(?:s|sday)?|Wed(?:nesday)?|Thu(?:r|rs|rsday)?|Fri(?:day)?|Sat(?:urday)?|Sun(?:day)?)\.?,?\s+""" +
            """(?:(\d{1,2})/(\d{1,2})|(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?|""" +
            """Sept?(?:ember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?\s+(\d{1,2}))(?!\d)""",
        RegexOption.IGNORE_CASE
    )
    private val YEAR = Regex("""\b(20\d\d)\b""")

    internal fun mentions(sourceText: String): List<Mention> =
        sourceText.lines().flatMap { line -> MENTION.findAll(line).mapNotNull { toMention(it, line) }.toList() }

    private fun toMention(m: MatchResult, line: String): Mention? {
        val weekday = WEEKDAYS[m.groupValues[1].take(3).lowercase()] ?: return null
        val month = if (m.groupValues[2].isNotEmpty()) m.groupValues[2].toInt()
        else MONTHS.indexOf(m.groupValues[4].take(3).lowercase()) + 1
        val day = (m.groupValues[3].ifEmpty { m.groupValues[5] }).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return Mention(m.value.trim(), weekday, month, day, line.trim())
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate(year, month, day) }.getOrNull()

    private fun agrees(mention: Mention, year: Int): Boolean =
        dateOrNull(year, mention.month, mention.day)?.dayOfWeek == mention.weekday

    /** The year whose calendar agrees with the most mentions, or null when that is not clear enough to trust. */
    internal fun bestYear(mentions: List<Mention>, candidateYears: Set<Int>): Int? {
        if (mentions.isEmpty()) return null
        val scored = candidateYears.associateWith { y -> mentions.count { agrees(it, y) } }
        val top = scored.maxByOrNull { it.value } ?: return null
        val unique = scored.count { it.value == top.value } == 1
        val clear = top.value >= MIN_AGREEING && top.value >= MIN_AGREEMENT_RATIO * mentions.size
        return top.key.takeIf { unique && clear }
    }

    /** The date within three days of [stated] that falls on [weekday]: the other reading of a bad date. */
    private fun weekdayReading(stated: LocalDate, weekday: DayOfWeek): LocalDate {
        val delta = (weekday.ordinal - stated.dayOfWeek.ordinal + 10) % 7 - 3
        return stated.plus(DatePeriod(days = delta))
    }

    internal fun conflicts(sourceText: String, events: List<Event>): List<Conflict> {
        val all = mentions(sourceText)
        val years = YEAR.findAll(sourceText).map { it.groupValues[1].toInt() }.toSet() + events.map { it.date.year }
        val year = bestYear(all, years) ?: return emptyList()
        return all.filter { !agrees(it, year) }.mapNotNull { m ->
            val stated = dateOrNull(year, m.month, m.day) ?: return@mapNotNull null
            Conflict(m, year, setOf(stated, weekdayReading(stated, m.weekday)))
        }
    }

    private fun reason(c: Conflict): String {
        val stated = LocalDate(c.year, c.mention.month, c.mention.day)
        val actual = stated.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        return "The source says \"${c.mention.text}\", but ${stated.month.name.lowercase().replaceFirstChar { it.uppercase() }} " +
            "${stated.dayOfMonth}, ${c.year} is a $actual. The source contradicts itself, so this date was not guessed. " +
            "Pick the correct date, or discard the event."
    }

    private val TITLE_FILLER = setOf("a", "an", "the", "of", "for", "to", "due", "by", "on", "in")

    private fun words(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in TITLE_FILLER }.toSet()

    /** Fraction of the event title's words that appear on the conflict's source line (digits count: "Speech 1" vs "Speech 2"). */
    private fun coverage(title: String, line: String): Double {
        val t = words(title)
        return if (t.isEmpty()) 0.0 else t.count { it in words(line) }.toDouble() / t.size
    }

    /**
     * The conflict an [event] belongs to: its date is one of the two readings AND its title words are on that
     * conflict's source line. The date test is already narrow, so title overlap only has to rule out coincidences.
     */
    private fun conflictFor(event: Event, conflicts: List<Conflict>): Conflict? =
        conflicts.filter { event.date in it.candidates }
            .map { it to coverage(event.title, it.mention.line) }
            .filter { it.second >= MIN_TITLE_COVERAGE }
            .maxByOrNull { it.second }?.first

    /**
     * Splits [events] into those safe to push and those held back because they sit on a weekday/date conflict in
     * [sourceText]. Held events keep the model's date untouched; the student decides.
     */
    fun partition(events: List<Event>, sourceText: String): Pair<List<Event>, List<DateResolutionItem>> {
        val found = conflicts(sourceText, events)
        if (found.isEmpty()) return events to emptyList()
        val held = mutableListOf<DateResolutionItem>()
        val clean = events.filter { event ->
            val c = conflictFor(event, found)
            if (c != null) held += DateResolutionItem(
                event, c.mention.line, reason(c), pushOnConfirm = true,
                statedDate = LocalDate(c.year, c.mention.month, c.mention.day)
            )
            c == null
        }
        return clean to held
    }
}
