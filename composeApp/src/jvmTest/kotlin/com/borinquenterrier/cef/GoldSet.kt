package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Hand-labeled "gold set" for extraction quality (T4 of tasks/best-practices-review.md).
 * One JSON file per syllabus under `composeApp/src/commonTest/resources/gold/`, naming the PDF (relative to
 * `contributions/`), its institution, and the events the syllabus itself states. REPORT-ONLY: nothing here
 * gates CI; thresholds come from observed data (owner rule, ADR 0004).
 */
data class GoldEvent(val title: String, val date: LocalDate)

data class GoldDocument(
    val id: String,
    val pdfRelativePath: String,
    val institution: String,
    val events: List<GoldEvent>
)

@Serializable
data class GoldInstitutionMetric(val expectedCount: Int, val matchedCount: Int, val dateCorrectCount: Int) {
    val recallPercent: Double get() = if (expectedCount == 0) 100.0 else matchedCount * 100.0 / expectedCount
    val dateAccuracyPercent: Double get() = if (matchedCount == 0) 100.0 else dateCorrectCount * 100.0 / matchedCount
}

@Serializable
data class GoldEvalMetrics(
    val overallRecallPercent: Double,
    val overallDateAccuracyPercent: Double,
    val perInstitution: Map<String, GoldInstitutionMetric>,
    val perFile: Map<String, SyllabusFileMetric>,
    val modelUsed: String? = null,
    val failedFiles: List<String> = emptyList()
)

data class GoldScore(val expectedCount: Int, val matchedCount: Int, val dateCorrectCount: Int, val missing: List<GoldEvent>, val dateMismatches: List<String>)

object GoldSetScorer {

    private val filler = setOf("a", "an", "the", "to", "of", "for", "this", "your", "on", "in")

    private fun normalize(s: String) = s.lowercase()
        .replace(Regex("['\"#’“”]"), "")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Same notion of "similar title" as SyllabusEvaluationIntegrationTest: substring, or token-subset ignoring filler. */
    fun titlesSimilar(a: String, b: String): Boolean {
        val n1 = normalize(a)
        val n2 = normalize(b)
        if (n1.isEmpty() || n2.isEmpty()) return false
        if (n1.contains(n2) || n2.contains(n1)) return true
        fun tokens(s: String) = s.split(" ").filter { it.isNotBlank() && it !in filler }.toSet()
        val t1 = tokens(n1)
        val t2 = tokens(n2)
        if (t1.isEmpty() || t2.isEmpty()) return false
        val (small, large) = if (t1.size <= t2.size) t1 to t2 else t2 to t1
        return large.containsAll(small)
    }

    /**
     * One-to-one matching: each extracted event can satisfy at most one expected event, and among
     * title-similar candidates the one on the expected date wins. This keeps "Exam I" from being
     * satisfied by "Exam II" (substring match) and stops two expectations sharing one extracted event.
     */
    fun score(expected: List<GoldEvent>, extracted: List<Event>): GoldScore {
        val used = mutableSetOf<Int>()
        var matched = 0
        var dateCorrect = 0
        val missing = mutableListOf<GoldEvent>()
        val mismatches = mutableListOf<String>()
        for (exp in expected) {
            val candidates = extracted.withIndex().filter { (i, e) -> i !in used && titlesSimilar(e.title, exp.title) }
            val pick = candidates.firstOrNull { it.value.date == exp.date } ?: candidates.firstOrNull()
            if (pick == null) {
                missing += exp
                continue
            }
            used += pick.index
            matched++
            if (pick.value.date == exp.date) dateCorrect++
            else mismatches += "'${exp.title}': expected ${exp.date}, got ${pick.value.date}"
        }
        return GoldScore(expected.size, matched, dateCorrect, missing, mismatches)
    }
}

object GoldSet {
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root (no settings.gradle.kts found)")
    }

    fun goldDir(root: File = repoRoot()) = File(root, "composeApp/src/commonTest/resources/gold")

    fun pdfFile(doc: GoldDocument, root: File = repoRoot()) = File(root, "contributions/${doc.pdfRelativePath}")

    fun parse(id: String, text: String): GoldDocument {
        val obj = Json.parseToJsonElement(text).jsonObject
        return GoldDocument(
            id = id,
            pdfRelativePath = obj["pdf"]!!.jsonPrimitive.content,
            institution = obj["institution"]!!.jsonPrimitive.content,
            events = obj["events"]!!.jsonArray.map {
                val e = it.jsonObject
                GoldEvent(e["title"]!!.jsonPrimitive.content, LocalDate.parse(e["date"]!!.jsonPrimitive.content))
            }
        )
    }

    fun load(dir: File = goldDir()): List<GoldDocument> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedBy { it.name }
            .map { parse(it.nameWithoutExtension, it.readText()) }
}
