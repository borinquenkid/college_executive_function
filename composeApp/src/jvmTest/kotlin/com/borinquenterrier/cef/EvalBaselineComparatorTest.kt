package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Unit tests for [EvalBaselineComparator] (ADR 0004 / ROADMAP Phase 13 EB-3). No live API calls.
 *
 * Each test uses its own fresh temp directory via [EvalBaselineComparator.buildReport]'s `dir`
 * parameter, rather than the real checked-in `evals/`. An earlier version of this test mutated
 * the real directory directly (backup/restore around each test) — that raced with
 * [EvalBaselineTest] once real `baseline_*.json`/`current_*.json` files existed there for real
 * (caught by an actual live-Gemini baseline-recording run, not a hypothetical), since both test
 * classes touched the same fixed filenames concurrently.
 */
class EvalBaselineComparatorTest : FunSpec({

    fun tempEvalsDir(): File = kotlin.io.path.createTempDirectory("eval-comparator-test").toFile()

    test("missing baseline/current files produce a skip note, not a crash") {
        val dir = tempEvalsDir()
        try {
            val report = EvalBaselineComparator.buildReport(dir)
            report shouldContain "skipping (not a failure"
        } finally {
            dir.deleteRecursively()
        }
    }

    test("identical baseline and current produce zero delta, marked OK") {
        val dir = tempEvalsDir()
        try {
            val metrics = SyllabusEvalMetrics(
                overallRecallPercent = 90.0, overallDateAccuracyPercent = 95.0, perFile = emptyMap()
            )
            EvalBaseline.writeCurrent("syllabus", SyllabusEvalMetrics.serializer(), metrics, dir)
            File(dir, "baseline_syllabus.json").writeText(File(dir, "current_syllabus.json").readText())

            val report = EvalBaselineComparator.buildReport(dir)
            report shouldContain "+0.0"
            report shouldContain "OK"
            report.contains("DRIFT") shouldBe false
        } finally {
            dir.deleteRecursively()
        }
    }

    test("a regressed current metric beyond tolerance is flagged as DRIFT") {
        val dir = tempEvalsDir()
        try {
            File(dir, "baseline_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{}}"""
            )
            File(dir, "current_syllabus.json").writeText(
                """{"overallRecallPercent":40.0,"overallDateAccuracyPercent":95.0,"perFile":{}}"""
            )

            val report = EvalBaselineComparator.buildReport(dir)
            report shouldContain "DRIFT"
        } finally {
            dir.deleteRecursively()
        }
    }
})
