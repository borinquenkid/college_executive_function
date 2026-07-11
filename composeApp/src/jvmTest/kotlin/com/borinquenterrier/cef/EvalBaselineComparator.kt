package com.borinquenterrier.cef

import java.io.File

/**
 * Diffs each eval-shaped class's freshly-computed `evals/current_*.json` against the checked-in
 * `evals/baseline_*.json` and prints a Markdown delta table (ADR 0004 / ROADMAP Phase 13 EB-3).
 *
 * Visibility only — does not fail the build. The existing `maxAllowedFailures` / threshold
 * assertions in the 3 eval-shaped test classes remain the actual pass/fail gate. Run after
 * `jvmTest -PrunAITests=true` so `evals/current_*.json` is fresh; writes to `$GITHUB_STEP_SUMMARY`
 * when set (nightly workflow), otherwise stdout (local/manual runs).
 *
 * A tolerance band (not exact-match) absorbs live-Gemini sampling noise — see [TOLERANCE_PERCENT].
 */
object EvalBaselineComparator {

    /** Percentage-point drift within this band is not flagged as a regression. */
    const val TOLERANCE_PERCENT = 5.0

    private data class DeltaRow(val name: String, val metric: String, val baseline: Double, val current: Double) {
        val delta: Double get() = current - baseline
        val withinTolerance: Boolean get() = kotlin.math.abs(delta) <= TOLERANCE_PERCENT
    }

    private fun syllabusRows(dir: File): List<DeltaRow> {
        val baseline = EvalBaseline.readBaseline("syllabus", SyllabusEvalMetrics.serializer(), dir)
        val current = EvalBaseline.readCurrent("syllabus", SyllabusEvalMetrics.serializer(), dir)
        if (baseline == null || current == null) return emptyList()
        return listOf(
            DeltaRow("syllabus", "overallRecallPercent", baseline.overallRecallPercent, current.overallRecallPercent),
            DeltaRow(
                "syllabus", "overallDateAccuracyPercent",
                baseline.overallDateAccuracyPercent, current.overallDateAccuracyPercent
            )
        )
    }

    private fun contributorPdfRows(dir: File): List<DeltaRow> {
        val baseline = EvalBaseline.readBaseline("contributor_pdf", ContributorPdfEvalMetrics.serializer(), dir)
        val current = EvalBaseline.readCurrent("contributor_pdf", ContributorPdfEvalMetrics.serializer(), dir)
        if (baseline == null || current == null) return emptyList()
        return listOf(
            DeltaRow(
                "contributor_pdf", "failedPercent",
                baseline.failedCount * 100.0 / baseline.totalFiles.coerceAtLeast(1),
                current.failedCount * 100.0 / current.totalFiles.coerceAtLeast(1)
            )
        )
    }

    private fun stlccRows(dir: File): List<DeltaRow> {
        val baseline = EvalBaseline.readBaseline("stlcc", StlccEvalMetrics.serializer(), dir)
        val current = EvalBaseline.readCurrent("stlcc", StlccEvalMetrics.serializer(), dir)
        if (baseline == null || current == null) return emptyList()
        val docs = baseline.perDocument.keys intersect current.perDocument.keys
        return docs.map { doc ->
            val b = baseline.perDocument.getValue(doc)
            val c = current.perDocument.getValue(doc)
            DeltaRow("stlcc:$doc", "duplicateCount", b.duplicateCount.toDouble(), c.duplicateCount.toDouble())
        }
    }

    fun buildReport(dir: File = EvalBaseline.defaultEvalsDir()): String {
        val allSections = listOf("syllabus" to syllabusRows(dir), "contributor_pdf" to contributorPdfRows(dir), "stlcc" to stlccRows(dir))
        val sb = StringBuilder()
        sb.appendLine("## Eval Baseline Delta")
        sb.appendLine()

        val missing = allSections.filter { it.second.isEmpty() }.map { it.first }
        if (missing.isNotEmpty()) {
            sb.appendLine(
                "> No baseline and/or current metrics found for: ${missing.joinToString(", ")} " +
                    "— skipping (not a failure; run `-PrecordEvalBaseline=true` once to seed a baseline)."
            )
            sb.appendLine()
        }

        val rows = allSections.flatMap { it.second }
        if (rows.isEmpty()) {
            sb.appendLine("No comparable metrics available yet.")
            return sb.toString()
        }

        sb.appendLine("| Class | Metric | Baseline | Current | Delta | Status |")
        sb.appendLine("|---|---|---|---|---|---|")
        rows.forEach { row ->
            val status = if (row.withinTolerance) "OK" else "DRIFT"
            sb.appendLine(
                "| ${row.name} | ${row.metric} | %.1f".format(row.baseline) +
                    " | %.1f".format(row.current) +
                    " | %+.1f".format(row.delta) +
                    " | $status |"
            )
        }
        return sb.toString()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val report = buildReport()
        println(report)

        val summaryPath = System.getenv("GITHUB_STEP_SUMMARY")
        if (!summaryPath.isNullOrBlank()) {
            File(summaryPath).appendText(report + "\n")
        }
    }
}
