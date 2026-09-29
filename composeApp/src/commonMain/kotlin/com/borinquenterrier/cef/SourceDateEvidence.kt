package com.borinquenterrier.cef

/**
 * Cheap, deterministic check for whether a document contains anything a calendar date could have
 * been read from. A document with no month-day, numeric or ISO date cannot ground an extracted
 * event, so any event the model returns for it is invented (a due-date-free project handout came
 * back with "2024-01-01" and "today" dates, 2026-09-29).
 */
object SourceDateEvidence {
    private const val MONTH =
        "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?|Aug(?:ust)?|" +
            "Sept?(?:ember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"

    private val PATTERNS = listOf(
        Regex("""\b$MONTH\.?\s+\d{1,2}\b""", RegexOption.IGNORE_CASE),                       // Sept. 28, October 13
        Regex("""\b\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH\b""", RegexOption.IGNORE_CASE), // 15 December
        Regex("""\b\d{1,2}/\d{1,2}\b"""),                                                      // 9/15
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),                                                    // 2026-09-03
    )

    fun hasDatableContent(text: String): Boolean = PATTERNS.any { it.containsMatchIn(text) }
}
