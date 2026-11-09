package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import kotlin.time.Duration.Companion.milliseconds

/**
 * REPORT-ONLY extraction-quality eval over the hand-labeled gold set (T4). Runs live Gemini calls (skips
 * cleanly with no key or an exhausted quota), prints per-file and per-institution recall/date accuracy,
 * and writes `evals/current_gold.json`. It asserts nothing about quality: thresholds are set from
 * observed data, not guesses (owner rule, ADR 0004).
 */
class GoldSetEvaluationIntegrationTest : FunSpec({

    test("Report extraction quality on the hand-labeled gold set").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds,
        // Kotest caps a single test at 10 min unless invocationTimeout is raised too (see ContributorPdfIntegrationTest).
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 20).milliseconds
    ) {
        val apiKey = resolveApiKey("GOLD SET EVALUATION") ?: return@config

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val aiService = RealAIService(settings, Logger(settings), database)

        val perFile = mutableMapOf<String, SyllabusFileMetric>()
        val perInstitution = mutableMapOf<String, GoldInstitutionMetric>()
        val failedFiles = mutableListOf<String>()
        val forbiddenHits = mutableMapOf<String, List<String>>()
        val heldBack = mutableMapOf<String, List<String>>()
        var expectedTotal = 0
        var matchedTotal = 0
        var dateCorrectTotal = 0

        val only = System.getProperty("goldFilter")
        for (doc in GoldSet.load().filter { only == null || it.id == only }) {
            val text = PdfReader().readSource(GoldSet.pdfFile(doc).absolutePath).joinToString("\n\n") { it.text }
            val fragments = SourceProcessor.process(text)
            // One file's API failure (timeouts, 5xx) must not discard the whole report: record it as a failed
            // file scored 0 matched, and continue. A quota abort (TestAbortedException) still skips cleanly.
            val extracted = try {
                skipIfQuotaExhausted("generateCalendarEvents[${doc.id}]") {
                    aiService.generateCalendarEvents(fragments)
                }
            } catch (e: org.opentest4j.TestAbortedException) {
                throw e
            } catch (e: Exception) {
                println("  ${doc.id}: EXTRACTION FAILED (${e::class.simpleName}: ${e.message?.take(120)})")
                failedFiles += doc.id
                emptyList()
            }
            // The product holds events on a weekday/date contradiction out of the push (never guessed); score only what it would push.
            val (pushable, held) = WeekdayDateConflictDetector.partition(extracted, text)
            if (held.isNotEmpty()) heldBack[doc.id] = held.map { "'${it.event.title}' @ ${it.event.date}" }
            held.forEach { println("    [HELD BACK] '${it.event.title}' @ ${it.event.date}: source contradicts itself") }
            val score = GoldSetScorer.score(doc.events, pushable)
            // Single-file runs (-PgoldFilter) are diagnostic: dump everything extracted so unlabeled behavior is visible.
            if (only != null) extracted.sortedBy { it.date }.forEach { println("    [EXTRACTED] ${it.date} ${it.category} '${it.title}'") }
            val forbidden = GoldSetScorer.forbiddenHits(doc.mustNotExtract, extracted)
            if (doc.mustNotExtract.isNotEmpty()) forbiddenHits[doc.id] = forbidden
            forbidden.forEach { println("    [FALSE POSITIVE] $it") }

            println("  ${doc.id}: recall ${score.matchedCount}/${score.expectedCount}, dates ${score.dateCorrectCount}/${score.matchedCount}")
            score.missing.forEach { m ->
                println("    [MISSING] '${m.title}' on ${m.date}")
                // Diagnostic: what did the extractor produce on that date? (separates a title mismatch from a true miss)
                extracted.filter { it.date == m.date }.forEach { println("      extracted same day: '${it.title}' (${it.category})") }
            }
            score.dateMismatches.forEach { println("    [DATE MISMATCH] $it") }

            perFile[doc.id] = SyllabusFileMetric(
                recallPercent = if (score.expectedCount == 0) 100.0 else score.matchedCount * 100.0 / score.expectedCount,
                dateAccuracyPercent = if (score.matchedCount == 0) 100.0 else score.dateCorrectCount * 100.0 / score.matchedCount,
                expectedCount = score.expectedCount,
                matchedCount = score.matchedCount
            )
            val prev = perInstitution[doc.institution] ?: GoldInstitutionMetric(0, 0, 0)
            perInstitution[doc.institution] = GoldInstitutionMetric(
                prev.expectedCount + score.expectedCount,
                prev.matchedCount + score.matchedCount,
                prev.dateCorrectCount + score.dateCorrectCount
            )
            expectedTotal += score.expectedCount
            matchedTotal += score.matchedCount
            dateCorrectTotal += score.dateCorrectCount
        }

        println("\n=== GOLD SET (report-only) ===")
        perInstitution.toSortedMap().forEach { (inst, m) ->
            println(String.format("%-12s recall %5.1f%% (%d/%d)  date accuracy %5.1f%%", inst, m.recallPercent, m.matchedCount, m.expectedCount, m.dateAccuracyPercent))
        }

        val checked = GoldSet.load().filter { only == null || it.id == only }.count { it.mustNotExtract.isNotEmpty() }
        println("FALSE POSITIVES (mustNotExtract): ${forbiddenHits.values.sumOf { it.size }} in ${forbiddenHits.count { it.value.isNotEmpty() }}/$checked files with negative labels")

        val modelUsed = database.appDatabaseQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        if (only == null) EvalBaseline.writeCurrent(
            "gold",
            GoldEvalMetrics.serializer(),
            GoldEvalMetrics(
                overallRecallPercent = if (expectedTotal == 0) 100.0 else matchedTotal * 100.0 / expectedTotal,
                overallDateAccuracyPercent = if (matchedTotal == 0) 100.0 else dateCorrectTotal * 100.0 / matchedTotal,
                perInstitution = perInstitution,
                perFile = perFile,
                modelUsed = modelUsed,
                failedFiles = failedFiles,
                forbiddenHits = forbiddenHits,
                heldBack = heldBack
            )
        )
        driver.close()
    }
})
