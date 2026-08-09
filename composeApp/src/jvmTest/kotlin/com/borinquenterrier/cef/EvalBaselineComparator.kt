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

    private fun retrievalRows(dir: File): List<DeltaRow> {
        val baseline = EvalBaseline.readBaseline("retrieval", RetrievalEvalMetrics.serializer(), dir)
        val current = EvalBaseline.readCurrent("retrieval", RetrievalEvalMetrics.serializer(), dir)
        if (baseline == null || current == null) return emptyList()
        val rows = mutableListOf(
            DeltaRow(
                "retrieval", "fragmentRecallAt15Percent",
                baseline.fragmentRecallAt15Percent, current.fragmentRecallAt15Percent
            ),
            DeltaRow(
                "retrieval", "promptContainsAnswerPercent",
                baseline.promptContainsAnswerPercent, current.promptContainsAnswerPercent
            )
        )
        (baseline.perBucket.keys intersect current.perBucket.keys).sorted().forEach { bucket ->
            rows += DeltaRow(
                "retrieval:$bucket", "promptContainsAnswerPercent",
                baseline.perBucket.getValue(bucket).promptContainsAnswerPercent,
                current.perBucket.getValue(bucket).promptContainsAnswerPercent
            )
        }
        return rows
    }

    /**
     * A metric delta is only interpretable as "did our code regress" when the underlying model
     * held steady — GeminiModelNegotiator's HEAVY-tier negotiation (see ROADMAP Phase 13) can
     * silently swap models between recording runs (deprecation, quota exhaustion, a new model
     * entering the preference list), which would otherwise be misread as a quality regression.
     * Absent on either side (older baselines recorded before [SyllabusEvalMetrics.modelUsed] et
     * al. existed) is not flagged — there's nothing to compare.
     */
    private fun modelChangeWarning(name: String, baselineModel: String?, currentModel: String?): String? {
        if (baselineModel == null || currentModel == null || baselineModel == currentModel) return null
        return "⚠️ **$name**: model changed since baseline (`$baselineModel` → `$currentModel`) — " +
            "treat the metric deltas above with caution; they may reflect the model swap, not a code regression."
    }

    private fun modelWarnings(dir: File): List<String> {
        val warnings = mutableListOf<String>()

        val syllabusBaseline = EvalBaseline.readBaseline("syllabus", SyllabusEvalMetrics.serializer(), dir)
        val syllabusCurrent = EvalBaseline.readCurrent("syllabus", SyllabusEvalMetrics.serializer(), dir)
        modelChangeWarning("syllabus", syllabusBaseline?.modelUsed, syllabusCurrent?.modelUsed)?.let(warnings::add)

        val contributorBaseline = EvalBaseline.readBaseline("contributor_pdf", ContributorPdfEvalMetrics.serializer(), dir)
        val contributorCurrent = EvalBaseline.readCurrent("contributor_pdf", ContributorPdfEvalMetrics.serializer(), dir)
        modelChangeWarning("contributor_pdf", contributorBaseline?.modelUsed, contributorCurrent?.modelUsed)?.let(warnings::add)

        val stlccBaseline = EvalBaseline.readBaseline("stlcc", StlccEvalMetrics.serializer(), dir)
        val stlccCurrent = EvalBaseline.readCurrent("stlcc", StlccEvalMetrics.serializer(), dir)
        if (stlccBaseline != null && stlccCurrent != null) {
            (stlccBaseline.perDocument.keys intersect stlccCurrent.perDocument.keys).forEach { doc ->
                modelChangeWarning(
                    "stlcc:$doc", stlccBaseline.perDocument[doc]?.modelUsed, stlccCurrent.perDocument[doc]?.modelUsed
                )?.let(warnings::add)
            }
        }
        return warnings
    }

    fun buildReport(dir: File = EvalBaseline.defaultEvalsDir()): String {
        val allSections = listOf(
            "syllabus" to syllabusRows(dir),
            "contributor_pdf" to contributorPdfRows(dir),
            "stlcc" to stlccRows(dir),
            "retrieval" to retrievalRows(dir)
        )
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

        val warnings = modelWarnings(dir)
        if (warnings.isNotEmpty()) {
            warnings.forEach { sb.appendLine("> $it") }
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
