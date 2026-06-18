package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

object WarningClassifier {

    // Phrases the AI emits to describe HOW it resolved ambiguity — never require user action
    private val informationalPatterns = listOf(
        Regex("specifies? (?:a )?range", RegexOption.IGNORE_CASE),
        Regex("not explicitly stated in source document", RegexOption.IGNORE_CASE),
        Regex("categorized as user.generated study block", RegexOption.IGNORE_CASE),
        Regex("interpreted as", RegexOption.IGNORE_CASE),
    )

    private val embeddedDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * Annotates [warning] with "— You can ignore this." when either:
     * - the text matches a known informational pattern, OR
     * - every date embedded in the warning falls outside [activeSemesterRange].
     *
     * Pass [activeSemesterRange] derived from event dates (not today) so summer-session
     * users processing a Fall syllabus still get the correct semester window.
     */
    fun classify(
        warning: String,
        activeSemesterRange: Pair<LocalDate, LocalDate>? = null
    ): String {
        if (informationalPatterns.any { it.containsMatchIn(warning) }) {
            return "$warning — You can ignore this."
        }

        if (activeSemesterRange != null) {
            val dates = embeddedDateRegex.findAll(warning)
                .mapNotNull { runCatching { LocalDate.parse(it.value) }.getOrNull() }
                .toList()
            if (dates.isNotEmpty() &&
                dates.all { it < activeSemesterRange.first || it > activeSemesterRange.second }
            ) {
                return "$warning — Out of current period, you can ignore this."
            }
        }

        return warning
    }

    /**
     * Returns the current semester range based on [today], or null if [events] is empty.
     * The semester is always derived from today, not from event dates — a user in summer
     * processing a Fall syllabus stays in the summer/interim window until Fall actually begins.
     */
    fun activeSemesterFrom(events: List<Event>, today: LocalDate): Pair<LocalDate, LocalDate>? {
        if (events.isEmpty()) return null
        return SemesterResolver.getSemesterRange(today)
    }
}
