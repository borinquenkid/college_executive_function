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
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 15).milliseconds
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
        var expectedTotal = 0
        var matchedTotal = 0
        var dateCorrectTotal = 0

        for (doc in GoldSet.load()) {
            val text = PdfReader().readSource(GoldSet.pdfFile(doc).absolutePath).joinToString("\n\n") { it.text }
            val fragments = SourceProcessor.process(text)
            val extracted = skipIfQuotaExhausted("generateCalendarEvents[${doc.id}]") {
                aiService.generateCalendarEvents(fragments)
            }
            val score = GoldSetScorer.score(doc.events, extracted)

            println("  ${doc.id}: recall ${score.matchedCount}/${score.expectedCount}, dates ${score.dateCorrectCount}/${score.matchedCount}")
            score.missing.forEach { println("    [MISSING] '${it.title}' on ${it.date}") }
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

        val modelUsed = database.appDatabaseQueries.getSelectedModel("preferred_gemini_model").executeAsOneOrNull()
        EvalBaseline.writeCurrent(
            "gold",
            GoldEvalMetrics.serializer(),
            GoldEvalMetrics(
                overallRecallPercent = if (expectedTotal == 0) 100.0 else matchedTotal * 100.0 / expectedTotal,
                overallDateAccuracyPercent = if (matchedTotal == 0) 100.0 else dateCorrectTotal * 100.0 / matchedTotal,
                perInstitution = perInstitution,
                perFile = perFile,
                modelUsed = modelUsed
            )
        )
        driver.close()
    }
})
