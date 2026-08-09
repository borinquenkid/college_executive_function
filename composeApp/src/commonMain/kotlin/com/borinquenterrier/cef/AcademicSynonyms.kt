package com.borinquenterrier.cef

/**
 * Curated academic synonym groups for query-side expansion (tasks/plan.md T5).
 *
 * Students — especially the executive-function-impaired students this app serves — ask about
 * coursework in their own words ("essay", "test", "buy books") while syllabi use formal
 * institutional vocabulary ("Issue Brief", "midterm examination", "required textbooks"). The
 * lexical rankers (FragmentRanker, Bm25Ranker) score zero on that mismatch. Expanding the
 * *query only* (never the documents) with same-group terms bridges the gap; BM25's IDF
 * naturally down-weights any expansion term that turns out to be common in the corpus, and
 * documents keep their original text so precision on verbatim queries is unaffected.
 *
 * Groups are plain data — extend them as real student phrasings surface in the retrieval eval
 * (RetrievalEvalTest measures exactly this gap via its paraphrase buckets). No stemming exists
 * anywhere in the pipeline, so singular/plural forms must both be listed where they matter.
 */
object AcademicSynonyms {

    private val groups: List<Set<String>> = listOf(
        // Written deliverables
        setOf("essay", "essays", "paper", "papers", "project", "projects", "brief", "briefs", "writeup", "write-ups", "report", "reports"),
        // Examinations
        setOf("exam", "exams", "test", "tests", "quiz", "quizzes", "midterm", "midterms", "final", "finals", "examination"),
        // Recurring work
        setOf("homework", "assignment", "assignments", "worksheet", "worksheets", "problems", "practice", "exercises"),
        // Deadlines and submission
        setOf("due", "deadline", "deadlines", "submit", "submission", "cutoff"),
        // Class meetings
        setOf("class", "classes", "lecture", "lectures", "session", "sessions", "meeting", "meetings"),
        // Course materials
        setOf("book", "books", "textbook", "textbooks", "materials", "readings"),
        // Attendance and requirements
        setOf("attendance", "attend", "mandatory", "compulsory", "required"),
        // Lateness and penalties
        setOf("late", "grace", "penalty", "penalties", "extension", "extensions"),
        // Dropping and withdrawal
        setOf("drop", "dropping", "withdraw", "withdrawal", "unenroll")
    )

    private val byTerm: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        groups.forEach { group ->
            group.forEach { term ->
                getOrPut(term) { mutableSetOf() }.addAll(group - term)
            }
        }
    }

    /**
     * Returns [query] with same-group synonyms of its terms appended (terms already present are
     * not duplicated). Queries touching no group pass through unchanged.
     */
    fun expandQuery(query: String): String {
        val queryTerms = query.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotEmpty() }
            .toSet()
        val expansions = queryTerms
            .flatMap { byTerm[it] ?: emptySet() }
            .toSet() - queryTerms
        return if (expansions.isEmpty()) query
        else query + " " + expansions.sorted().joinToString(" ")
    }
}
