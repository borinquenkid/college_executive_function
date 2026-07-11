package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Round-trip serder tests for the eval-baseline metric DTOs (per this repo's convention that
 * every new @Serializable DTO gets a round-trip test) plus recording-flag/file-layout behavior.
 * No live API calls — this is a plain unit test, not an *IntegrationTest.
 */
class EvalBaselineTest : FunSpec({

    val json = Json { prettyPrint = true }

    test("SyllabusEvalMetrics round-trips through JSON") {
        val metrics = SyllabusEvalMetrics(
            overallRecallPercent = 87.5,
            overallDateAccuracyPercent = 100.0,
            perFile = mapOf(
                "syllabus_bdan250.pdf" to SyllabusFileMetric(
                    recallPercent = 100.0, dateAccuracyPercent = 100.0, expectedCount = 4, matchedCount = 4
                )
            )
        )
        val encoded = json.encodeToString(SyllabusEvalMetrics.serializer(), metrics)
        val decoded = json.decodeFromString(SyllabusEvalMetrics.serializer(), encoded)
        decoded shouldBe metrics
    }

    test("ContributorPdfEvalMetrics round-trips through JSON") {
        val metrics = ContributorPdfEvalMetrics(
            totalFiles = 19,
            failedCount = 1,
            perFile = mapOf(
                "tx/ut_austin/2025-2026/fall/BIO325_genetics.pdf" to ContributorFileMetric(
                    eventCount = 12, hasNonExam = true, passed = true
                )
            )
        )
        val encoded = json.encodeToString(ContributorPdfEvalMetrics.serializer(), metrics)
        val decoded = json.decodeFromString(ContributorPdfEvalMetrics.serializer(), encoded)
        decoded shouldBe metrics
    }

    test("StlccEvalMetrics round-trips through JSON") {
        val metrics = StlccEvalMetrics(
            perDocument = mapOf(
                "STLCC_ENG101_WEEKLY" to StlccDocMetric(eventCount = 40, duplicateCount = 0, modelStable = true)
            )
        )
        val encoded = json.encodeToString(StlccEvalMetrics.serializer(), metrics)
        val decoded = json.decodeFromString(StlccEvalMetrics.serializer(), encoded)
        decoded shouldBe metrics
    }

    test("isRecordingEnabled reflects the recordEvalBaseline system property") {
        val original = System.getProperty("recordEvalBaseline")
        try {
            System.clearProperty("recordEvalBaseline")
            EvalBaseline.isRecordingEnabled() shouldBe false

            System.setProperty("recordEvalBaseline", "true")
            EvalBaseline.isRecordingEnabled() shouldBe true
        } finally {
            if (original != null) System.setProperty("recordEvalBaseline", original)
            else System.clearProperty("recordEvalBaseline")
        }
    }

    test("writeCurrent always writes current_<name>.json, and only writes baseline_<name>.json when recording is enabled") {
        // Uses an isolated temp directory (via writeCurrent's dir parameter), not the real
        // checked-in evals/ — this test class previously mutated evals/ directly, which raced
        // with EvalBaselineComparatorTest's tests once real baseline/current files existed there
        // (caught by an actual live-Gemini recording run, not a hypothetical).
        val tempDir = kotlin.io.path.createTempDirectory("eval-baseline-test").toFile()
        val currentFile = File(tempDir, "current_unittestprobe.json")
        val baselineFile = File(tempDir, "baseline_unittestprobe.json")
        val original = System.getProperty("recordEvalBaseline")
        try {
            System.clearProperty("recordEvalBaseline")

            val metrics = StlccEvalMetrics(perDocument = emptyMap())
            EvalBaseline.writeCurrent("unittestprobe", StlccEvalMetrics.serializer(), metrics, tempDir)

            currentFile.exists() shouldBe true
            baselineFile.exists() shouldBe false

            System.setProperty("recordEvalBaseline", "true")
            EvalBaseline.writeCurrent("unittestprobe", StlccEvalMetrics.serializer(), metrics, tempDir)
            baselineFile.exists() shouldBe true
        } finally {
            if (original != null) System.setProperty("recordEvalBaseline", original)
            else System.clearProperty("recordEvalBaseline")
            tempDir.deleteRecursively()
        }
    }
})
