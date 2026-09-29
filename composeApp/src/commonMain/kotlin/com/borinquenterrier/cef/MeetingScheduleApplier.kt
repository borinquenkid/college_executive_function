package com.borinquenterrier.cef

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Applies a syllabus's stated [MeetingPattern] to its extracted events:
 *
 * 1. **Times** — date-only CLASS events on a meeting weekday get the stated class time.
 * 2. **Expansion** — a syllabus that states "MWF 9:00" but has no per-session schedule (Missouri S&T
 *    MATH 4209: "Course Schedule: This will be updated soon") yields no class sessions at all. When
 *    fewer than [SPARSE_CLASS_DATES] class dates were extracted and the student has set a semester
 *    window, sessions are generated on the meeting days, each carrying a warning that it was
 *    inferred. Dates are skipped when they are a holiday/break/no-class day or an exam day in this
 *    syllabus, fall inside a break range stated in its text, or are an institutional holiday/break in
 *    another loaded source (the school calendar). Generation starts at "classwork begins" and stops at
 *    "last class day" or the Monday of finals week when either is known.
 *
 * This deliberately goes further than [ClassMeetingReconciler] (which fills at most two gaps in an
 * observed grid): here the pattern is stated fact in the source, not inferred from the model's output.
 */
object MeetingScheduleApplier {
    const val SPARSE_CLASS_DATES = 6

    private val NO_CLASS_MARKERS = listOf("no class", "holiday", "break", "vacation", "no in-person", "cancel")
    private val BREAK_MARKERS = listOf("break", "vacation")

    // Other sources' events only block this course when they are institution-wide — a different
    // course's "No Class (Conference)" must not cancel this one.
    private val INSTITUTIONAL_MARKERS = listOf(
        "break", "vacation", "holiday", "labor day", "thanksgiving", "veteran", "memorial day",
        "martin luther king", "independence day", "juneteenth", "no classes"
    )
    private val CLASSES_BEGIN = Regex("""(?i)class(?:es|work)? begin|first day of class""")
    private val LAST_CLASS_DAY = Regex("""(?i)last class day|last day of class""")
    private val FINALS = Regex("""(?i)final exam|finals week|final examinations""")
    private val REVIEW = Regex("""(?i)\breview\b""")
    private val INSTITUTIONAL_FINALS = Regex("""(?i)final exams\b|finals week|final examinations|final exam week""")
    // "Last Class Day - First Eight-week Session": part-of-term rows in a school calendar.
    private val SUB_SESSION = Regex("""(?i)\b(?:\d{1,2}|four|six|eight|twelve)[- ]week|\bsession\b""")

    /** Dates of [events] whose title matches [marker], preferring full-term rows over sub-session rows. */
    private fun datesOf(events: List<Event>, marker: Regex): List<LocalDate> {
        val matching = events.filter { marker.containsMatchIn(it.title) }
        return matching.filterNot { SUB_SESSION.containsMatchIn(it.title) }.ifEmpty { matching }.map { it.date }
    }

    private const val MONTH =
        "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?"
    private val TEXT_BREAK_RANGE = Regex("""(?i)\b$MONTH\s+(\d{1,2})\s*[–—-]\s*(?:$MONTH\s+)?(\d{1,2})\b""")
    private val ANY_DATE = Regex("""(?i)\b$MONTH\s+\d{1,2}\b|\b\d{1,2}/\d{1,2}\b""")

    fun apply(
        events: List<Event>,
        sourceText: String,
        window: Pair<LocalDate, LocalDate>?,
        otherSourcesEvents: List<Event> = emptyList()
    ): List<Event> {
        val pattern = MeetingPatternParser.parse(sourceText) ?: return events
        val term = window ?: termFromCalendar(events, otherSourcesEvents)
        val expanded = if (term != null) expandIfSparse(events, pattern, sourceText, term, otherSourcesEvents) else events
        return if (pattern.hasTime) attachTimes(expanded, pattern) else expanded
    }

    private fun attachTimes(events: List<Event>, pattern: MeetingPattern): List<Event> = events.map { event ->
        if (event is DayEvent && event.category == AcademicCategory.CLASS && event.date.dayOfWeek in pattern.days) {
            TimeEvent(
                id = event.id, title = event.title, source = event.source, category = event.category,
                syncStatus = event.syncStatus, updatedAt = event.updatedAt, warning = event.warning,
                studyPlanStart = event.studyPlanStart, gradeWeight = event.gradeWeight,
                completionStatus = event.completionStatus, sourceId = event.sourceId,
                startTime = pattern.start!!, endTime = pattern.end!!, date = event.date
            )
        } else {
            event
        }
    }

    private fun expandIfSparse(
        events: List<Event>,
        pattern: MeetingPattern,
        sourceText: String,
        window: Pair<LocalDate, LocalDate>,
        others: List<Event>
    ): List<Event> {
        if (events.filter { it.category == AcademicCategory.CLASS }.map { it.date }.distinct().size >= SPARSE_CLASS_DATES) {
            return events
        }
        // No session schedule to source it from: a model CLASS event off the stated meeting days is
        // invented (MATH 4209, MWF, came back with a Tuesday "Class Session", 2026-09-29).
        val kept = events.filterNot { it.category == AcademicCategory.CLASS && it.date.dayOfWeek !in pattern.days }
        val classDates = kept.filter { it.category == AcademicCategory.CLASS }.map { it.date }.toSet()

        val institutional = others.filter { e -> INSTITUTIONAL_MARKERS.any { it in e.title.lowercase() } }
        val blocked = mutableSetOf<LocalDate>()
        for (e in events) {
            val title = e.title.lowercase()
            if (e.category == AcademicCategory.HOLIDAY || e.category == AcademicCategory.FINALS ||
                NO_CLASS_MARKERS.any { it in title }
            ) blocked += e.date
            if (BREAK_MARKERS.any { it in title }) blocked += breakSpan(e.date)
        }
        for (e in institutional) {
            blocked += e.date
            if (BREAK_MARKERS.any { it in e.title.lowercase() }) blocked += breakSpan(e.date)
        }
        blocked += textBreakRanges(sourceText, window.first)

        val all = events + others
        val start = datesOf(all, CLASSES_BEGIN)
            .filter { it in window.first..window.second }.minOrNull() ?: window.first
        val lastClassDay = datesOf(all, LAST_CLASS_DAY)
            .filter { it in window.first..window.second }.minOrNull()
        // Finals cut the term off only when they are this course's own final or the institution's
        // exam period — another course's "Final Exam Review" (a class session) must not.
        val finalsRows = events.filter { FINALS.containsMatchIn(it.title) && !REVIEW.containsMatchIn(it.title) } +
            others.filter { INSTITUTIONAL_FINALS.containsMatchIn(it.title) }
        val finalsMonday = datesOf(finalsRows, FINALS)
            .filter { it in window.first..window.second }.minOrNull()
            ?.let { it.minus(DatePeriod(days = it.dayOfWeek.isoDayNumber - 1)) }
        val end = listOfNotNull(window.second, lastClassDay, finalsMonday?.minus(DatePeriod(days = 1))).min()

        val slot = if (pattern.hasTime) "${pattern.start}–${pattern.end}" else ""
        val daysLabel = pattern.days.sorted().joinToString("/") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        val title = courseLabel(sourceText)?.let { "$it class" } ?: "Class meeting"
        val warning = "Added automatically from the syllabus's stated meeting pattern ($daysLabel $slot)".replace(" )", ")") +
            " — the syllabus has no session-by-session schedule, so double-check holidays and cancellations."

        val generated = generateSequence(start) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= end }
            .filter { it.dayOfWeek in pattern.days && it !in classDates && it !in blocked }
            .map { date ->
                DayEvent(
                    title = title, source = EventSource.AI_GENERATED, category = AcademicCategory.CLASS,
                    date = date, warning = warning
                )
            }
            .toList()
        return kept + generated
    }

    /**
     * Term bounds from a loaded school calendar when the student hasn't set a semester window: the
     * earliest "classes begin" within [MAX_TERM_DAYS] before this syllabus's own dates, through the
     * first "last class day" after them. Earliest-begin / first-end-after keeps 8-week sub-session
     * rows from narrowing a full-term course; anchoring on the syllabus's dates picks the right term
     * out of a multi-term calendar. Null when the syllabus has no dates to anchor on.
     */
    internal fun termFromCalendar(own: List<Event>, others: List<Event>): Pair<LocalDate, LocalDate>? {
        val anchor = own.map { it.date }.sorted().let { if (it.isEmpty()) return null else it[it.size / 2] }
        val end = datesOf(others, LAST_CLASS_DAY).filter { it >= anchor }.minOrNull() ?: return null
        // Earliest begin that still fits one term with that end: a summer "Classes Begin" row can sit
        // within MAX_TERM_DAYS of a fall anchor, but not of the fall's last class day (2026-09-29).
        val start = datesOf(others, CLASSES_BEGIN)
            .filter { it <= anchor && it.daysUntil(end) <= MAX_TERM_DAYS }.minOrNull() ?: return null
        return start to end
    }

    private const val MAX_TERM_DAYS = 140

    /**
     * A class session before the source's own "classes begin" row or after its "last class day" can't
     * be real. UT BIO 325L: an undated batch came back "Class Lecture @ 2026-01-01" (shifted to Monday
     * 1/5 by the critic pass) a week before its stated "UT classes begin" 1/12, and the gap-filler then
     * added a "Class meeting" on 1/6 (2026-09-29). Runs before [ClassMeetingReconciler].
     */
    fun dropClassesOutsideOwnTerm(events: List<Event>): List<Event> {
        val begin = datesOf(events, CLASSES_BEGIN).minOrNull()
        val end = datesOf(events, LAST_CLASS_DAY).maxOrNull()
        if (begin == null && end == null) return events
        return events.filterNot { e ->
            e.category == AcademicCategory.CLASS &&
                ((begin != null && e.date < begin) || (end != null && e.date > end))
        }
    }

    /** A break on [date] runs through the following weekend (a weekend start covers the next week). */
    internal fun breakSpan(date: LocalDate): List<LocalDate> {
        val earliestEnd = date.plus(DatePeriod(days = 2))
        val sunday = earliestEnd.plus(DatePeriod(days = (7 - earliestEnd.dayOfWeek.isoDayNumber) % 7))
        return generateSequence(date) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= sunday }.toList()
    }

    /**
     * Ranges like "Nov. 23–27 Thanksgiving Break – No Class" on a line whose only date is that range.
     * A week row that merely *mentions* a break ("Sept. 7–11 Labor Day (Sept. 7 – No Class)") names a
     * second date and is skipped — that single day comes through as its own event instead.
     */
    internal fun textBreakRanges(text: String, termStart: LocalDate): Set<LocalDate> {
        val out = mutableSetOf<LocalDate>()
        for (line in text.lines()) {
            val lower = line.lowercase()
            if (NO_CLASS_MARKERS.none { it in lower }) continue
            val range = TEXT_BREAK_RANGE.find(line) ?: continue
            if (ANY_DATE.findAll(line).count() > 1 + (if (range.groupValues[3].isNotEmpty()) 1 else 0)) continue
            val startMonth = monthOf(range.groupValues[1]) ?: continue
            val endMonth = range.groupValues[3].ifEmpty { null }?.let { monthOf(it) } ?: startMonth
            val from = dateIn(termStart, startMonth, range.groupValues[2].toInt()) ?: continue
            val to = dateIn(termStart, endMonth, range.groupValues[4].toInt()) ?: continue
            if (to < from || from.daysUntil(to) > 16) continue
            out += generateSequence(from) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= to }
        }
        return out
    }

    private fun monthOf(token: String): Month? =
        Month.entries.firstOrNull { it.name.startsWith(token.take(3).uppercase()) }

    /** Month/day resolved to the term's year; months before the term's start month roll into next year. */
    private fun dateIn(termStart: LocalDate, month: Month, day: Int): LocalDate? {
        val year = if (month.ordinal < termStart.month.ordinal) termStart.year + 1 else termStart.year
        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    // Trailing (?!\d) rather than \b: DOCX text arrives glued ("SPMS 1185Sect. 111TR ...").
    private val COURSE_CODE = Regex("""\b([A-Z]{2,5}(?: [A-Z]{2,5})?) ?(\d{4}|\d{3})(?!\d)""")

    internal fun courseLabel(text: String): String? =
        COURSE_CODE.find(text)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }

    private fun Iterable<DayOfWeek>.sorted(): List<DayOfWeek> = sortedBy { it.isoDayNumber }
}
