package com.borinquenterrier.cef

/**
 * Extracts week-to-date anchor definitions from a document's fragments and injects
 * them as metadata into every fragment that references a week number without containing
 * the definition itself.
 *
 * Problem this solves: A syllabus like STLCC ENG 101 puts the assignment summary table
 * ("Issue Brief #1 — Due Week 4") on page 1, and the week date ranges
 * ("Week 4: June 29–July 5, 2026") on later pages. When the PDF is split one fragment
 * per page, the AI sees "Due Week 4" with no anchor to resolve it.
 *
 * The fix: collect all anchor definitions from all fragments in a pre-pass, then inject
 * the full anchor block into any fragment that has "Week N" references but no anchors.
 */
object WeekAnchorExtractor {

    // Matches: "Week 4: June 29–July 5, 2026" or "Week 4: June 29–July 5" or
    //          "Week 4: June 29 - July 5, 2026" (dash or en-dash)
    private val ANCHOR_PATTERN = Regex(
        """Week\s+(\d{1,2})\s*:\s*([A-Za-z]+ \d{1,2}[–\-][A-Za-z]* ?\d{1,2}(?:,?\s*\d{4})?)""",
        RegexOption.IGNORE_CASE
    )

    // Matches a bare "Week N" reference (not followed by a colon, so not an anchor itself)
    private val REFERENCE_PATTERN = Regex(
        """Week\s+\d{1,2}(?!\s*:)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Scans all fragments, collects every week anchor definition found anywhere in the
     * document, then returns a new list where fragments that reference week numbers but
     * lack anchors have the anchor table injected into their metadata["weekAnchors"].
     *
     * Fragments that already contain all needed anchors are returned unchanged.
     */
    fun inject(fragments: List<SourceFragment>): List<SourceFragment> {
        val allAnchors = collectAnchors(fragments)
        if (allAnchors.isEmpty()) return fragments

        val anchorBlock = allAnchors.entries
            .sortedBy { it.key }
            .joinToString("\n") { (n, range) -> "Week $n: $range" }

        return fragments.map { fragment ->
            if (needsAnchors(fragment, allAnchors)) {
                fragment.copy(
                    metadata = fragment.metadata + mapOf("weekAnchors" to anchorBlock)
                )
            } else {
                fragment
            }
        }
    }

    private fun collectAnchors(fragments: List<SourceFragment>): Map<Int, String> {
        val anchors = mutableMapOf<Int, String>()
        for (fragment in fragments) {
            for (match in ANCHOR_PATTERN.findAll(fragment.text)) {
                val weekNum = match.groupValues[1].toIntOrNull() ?: continue
                val range = match.groupValues[2].trim()
                anchors[weekNum] = range
            }
        }
        return anchors
    }

    // A fragment needs anchors injected if it references "Week N" AND is missing
    // at least one of those anchor definitions in its own text.
    private fun needsAnchors(fragment: SourceFragment, allAnchors: Map<Int, String>): Boolean {
        val refs = REFERENCE_PATTERN.findAll(fragment.text)
            .map { it.value.trim().removePrefix("Week").trim().toIntOrNull() }
            .filterNotNull()
            .toSet()
        if (refs.isEmpty()) return false

        // Check if this fragment already defines all the anchors it references
        val localAnchors = ANCHOR_PATTERN.findAll(fragment.text)
            .map { it.groupValues[1].toIntOrNull() }
            .filterNotNull()
            .toSet()

        return refs.any { it !in localAnchors && it in allAnchors }
    }
}
