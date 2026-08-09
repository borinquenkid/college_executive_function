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

    test("retrieval metrics produce overall and per-bucket delta rows") {
        val dir = tempEvalsDir()
        try {
            val metrics = RetrievalEvalMetrics(
                questionCount = 18,
                fragmentRecallAt5Percent = 77.8,
                fragmentRecallAt15Percent = 100.0,
                meanReciprocalRank = 0.6,
                promptContainsAnswerPercent = 88.9,
                perBucket = mapOf(
                    "paraphrase|deadline" to RetrievalBucketMetric(4, 100.0, 75.0)
                )
            )
            EvalBaseline.writeCurrent("retrieval", RetrievalEvalMetrics.serializer(), metrics, dir)
            File(dir, "baseline_retrieval.json").writeText(File(dir, "current_retrieval.json").readText())

            val report = EvalBaselineComparator.buildReport(dir)
            report shouldContain "| retrieval | promptContainsAnswerPercent |"
            report shouldContain "| retrieval:paraphrase|deadline | promptContainsAnswerPercent |"
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

    test("a model change between baseline and current is flagged as a warning, not silently folded into the delta") {
        val dir = tempEvalsDir()
        try {
            File(dir, "baseline_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{},"modelUsed":"gemini-2.5-flash"}"""
            )
            File(dir, "current_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{},"modelUsed":"gemini-2.5-pro"}"""
            )

            val report = EvalBaselineComparator.buildReport(dir)
            report shouldContain "model changed since baseline"
            report shouldContain "gemini-2.5-flash"
            report shouldContain "gemini-2.5-pro"
        } finally {
            dir.deleteRecursively()
        }
    }

    test("missing modelUsed on either side (older baselines) is not flagged") {
        val dir = tempEvalsDir()
        try {
            File(dir, "baseline_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{}}"""
            )
            File(dir, "current_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{},"modelUsed":"gemini-2.5-pro"}"""
            )

            val report = EvalBaselineComparator.buildReport(dir)
            report.contains("model changed") shouldBe false
        } finally {
            dir.deleteRecursively()
        }
    }

    test("same model on both sides is not flagged") {
        val dir = tempEvalsDir()
        try {
            File(dir, "baseline_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{},"modelUsed":"gemini-2.5-flash"}"""
            )
            File(dir, "current_syllabus.json").writeText(
                """{"overallRecallPercent":95.0,"overallDateAccuracyPercent":95.0,"perFile":{},"modelUsed":"gemini-2.5-flash"}"""
            )

            val report = EvalBaselineComparator.buildReport(dir)
            report.contains("model changed") shouldBe false
        } finally {
            dir.deleteRecursively()
        }
    }
})
