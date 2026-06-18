package com.borinquenterrier.cef

/**
 * Extracts specific factual claims (dates, grade weights) from free-text LLM output
 * and cross-checks them against a source text.
 *
 * A claim is "ungrounded" when it appears in the AI response but has no corresponding
 * mention anywhere in the source the model was given. Ungrounded claims are not dropped
 * (that would require sentence-level surgery), but a structured warning is appended so
 * the student knows which specific values to verify independently.
 *
 * Scope: dates expressed as "Month DD" and grade percentages expressed as "N%".
 * Ordinal suffixes (14th, 3rd) are stripped before matching so "October 14th" in a
 * response matches "October 14" in a syllabus.
 */
object SourceFactGrounder {

    // Matches "October 14", "Oct 14", "November 3rd", "Feb. 7th", etc.
    // Requires a 1–2-digit day number (with optional ordinal suffix) followed by a
    // non-word character or end of input, so "May 2025" and "August 2026" are excluded.
    private val MONTH_DAY_PATTERN = Regex(
        """\b(January|February|March|April|May|June|July|August|September|October|November|December""" +
        """|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\.?\s+\d{1,2}(?:st|nd|rd|th)?(?=\W|$)""",
        RegexOption.IGNORE_CASE
    )

    // Matches "40%", "15 %" — explicit numeric grade weight assertions.
    private val PERCENTAGE_PATTERN = Regex("""\b\d+\s*%""")

    fun extractClaims(text: String): List<String> =
        (MONTH_DAY_PATTERN.findAll(text) + PERCENTAGE_PATTERN.findAll(text))
            .map { normalizeOrdinals(it.value.trim()) }
            .distinct()
            .toList()

    fun findUngrounded(claims: List<String>, sourceText: String): List<String> =
        claims.filter { claim -> !sourceText.contains(claim, ignoreCase = true) }

    /**
     * Returns [response] unchanged when all factual claims are grounded in [sourceText].
     * Appends a structured warning when one or more claims cannot be verified.
     * Returns [response] unchanged when [sourceText] is blank (no corpus loaded).
     */
    fun groundFreeText(response: String, sourceText: String, logger: Logger?): String {
        if (sourceText.isBlank()) return response
        val claims = extractClaims(response)
        if (claims.isEmpty()) return response
        val ungrounded = findUngrounded(claims, sourceText)
        if (ungrounded.isEmpty()) return response
        logger?.d("SourceFactGrounder", "Ungrounded claims in response: $ungrounded")
        return "$response\n\n[Note: the following specific claims could not be verified " +
               "in your loaded syllabi: ${ungrounded.joinToString(", ")}. " +
               "Please confirm these directly with your course materials.]"
    }

    private fun normalizeOrdinals(text: String): String =
        text.replace(Regex("""(\d+)(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE), "$1")
}
