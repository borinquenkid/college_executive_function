package com.borinquenterrier.cef

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Metric-capture infra for the 3 eval-shaped integration classes (ADR 0004 / ROADMAP Phase 13).
 *
 * Every run writes its computed metrics to `evals/current_<name>.json` unconditionally — this
 * costs nothing extra since the numbers are already computed for the existing threshold
 * assertion. Promoting `current` to the checked-in `evals/baseline_<name>.json` only happens
 * when `-PrecordEvalBaseline=true` is passed, and is meant to be a human-reviewed, separately
 * committed action (see ADR 0004) — never auto-written by a routine CI run.
 */
object EvalBaseline {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun isRecordingEnabled(): Boolean =
        System.getProperty("recordEvalBaseline")?.toBoolean() == true

    /**
     * Walks up from the JVM process's working directory to find the repo root (marked by
     * `settings.gradle.kts`). Needed because `:composeApp:jvmTest`'s working directory is
     * `composeApp/`, not the repo root — a bug caught by actually running this against live
     * Gemini data: the first real recording run wrote to `composeApp/evals/` instead of the
     * checked-in `evals/` at repo root.
     */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root (no settings.gradle.kts found walking up from ${File(".").absolutePath})")
    }

    /**
     * The real checked-in `evals/` directory. Exposed (not private) only so tests can see what
     * production code resolves to — tests themselves should pass their own temp directory to the
     * `dir` parameter below rather than mutating this one, since a shared global path would let
     * concurrently-run test classes race on the same files (caught by exactly that happening once
     * real baseline/current JSON files started existing here after the first live-Gemini recording run).
     */
    fun defaultEvalsDir(): File = File(repoRoot(), "evals").also { it.mkdirs() }

    /** Always writes `current_<name>.json`; also promotes to `baseline_<name>.json` when recording is enabled. */
    fun <T> writeCurrent(name: String, serializer: KSerializer<T>, metrics: T, dir: File = defaultEvalsDir()) {
        val encoded = json.encodeToString(serializer, metrics)

        val currentFile = File(dir, "current_$name.json")
        currentFile.writeText(encoded)
        println("Eval metrics for '$name' written to ${currentFile.path}")

        if (isRecordingEnabled()) {
            val baselineFile = File(dir, "baseline_$name.json")
            baselineFile.writeText(encoded)
            println("Recorded new baseline for '$name' at ${baselineFile.path} — review before committing.")
        }
    }

    fun <T> readBaseline(name: String, serializer: KSerializer<T>, dir: File = defaultEvalsDir()): T? =
        readJson(name, "baseline", serializer, dir)

    fun <T> readCurrent(name: String, serializer: KSerializer<T>, dir: File = defaultEvalsDir()): T? =
        readJson(name, "current", serializer, dir)

    private fun <T> readJson(name: String, prefix: String, serializer: KSerializer<T>, dir: File): T? {
        val file = File(dir, "${prefix}_$name.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }
}

@Serializable
data class SyllabusFileMetric(
    val recallPercent: Double,
    val dateAccuracyPercent: Double,
    val expectedCount: Int,
    val matchedCount: Int
)

@Serializable
data class SyllabusEvalMetrics(
    val overallRecallPercent: Double,
    val overallDateAccuracyPercent: Double,
    val perFile: Map<String, SyllabusFileMetric>
)

@Serializable
data class ContributorFileMetric(
    val eventCount: Int,
    val hasNonExam: Boolean,
    val passed: Boolean
)

@Serializable
data class ContributorPdfEvalMetrics(
    val totalFiles: Int,
    val failedCount: Int,
    val perFile: Map<String, ContributorFileMetric>
)

@Serializable
data class StlccDocMetric(
    val eventCount: Int,
    val duplicateCount: Int,
    val modelStable: Boolean
)

@Serializable
data class StlccEvalMetrics(
    val perDocument: Map<String, StlccDocMetric>
)
