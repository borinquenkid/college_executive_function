package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import kotlin.time.Duration.Companion.milliseconds

/**
 * REPORT-ONLY per-document stability (T5): each gold-set file is extracted [runs] times with fresh services and
 * the event sets are compared. Writes `evals/current_stability.json`; asserts nothing (thresholds come from
 * observed data, ADR 0004). `-PgoldFilter=<id>` limits it to one file and skips the write.
 */
class StabilityEvaluationIntegrationTest : FunSpec({

    val runs = System.getProperty("stabilityRuns")?.toIntOrNull() ?: 3

    test("Report per-document extraction stability on the gold set").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * 40).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * 40).milliseconds
    ) {
        val apiKey = resolveApiKey("STABILITY EVALUATION") ?: return@config
        val only = System.getProperty("goldFilter")
        val perDocument = mutableMapOf<String, DocumentStability>()
        var modelUsed: String? = null

        for (doc in GoldSet.load().filter { only == null || it.id == only }) {
            val text = PdfReader().readSource(GoldSet.pdfFile(doc).absolutePath).joinToString("\n\n") { it.text }
            val fragments = SourceProcessor.process(text)
            val perRun = (1..runs).map { run ->
                // Fresh DB and service per run: no analysis cache, no cross-run state.
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                AppDatabase.Schema.create(driver)
                val database = AppDatabase(driver)
                val settings = MapSettings().apply { putString("CEF_GEMINI_API_KEY", apiKey) }
                val aiService = RealAIService(settings, Logger(settings), database)
                try {
                    val events = skipIfQuotaExhausted("generateCalendarEvents[${doc.id}#$run]") {
                        aiService.generateCalendarEvents(fragments)
                    }
                    modelUsed = database.appDatabaseQueries.getSelectedModel("preferred_gemini_model")
                        .executeAsOneOrNull() ?: modelUsed
                    events.map { StabilityEvent(it.date.toString(), it.title) }
                } catch (e: org.opentest4j.TestAbortedException) {
                    throw e
                } catch (e: Exception) {
                    println("  ${doc.id} run $run: EXTRACTION FAILED (${e::class.simpleName}: ${e.message?.take(100)})")
                    null
                } finally {
                    driver.close()
                }
            }
            val s = StabilityScorer.score(perRun)
            perDocument[doc.id] = s
            println(
                "  ${doc.id}: events ${s.minEvents}-${s.maxEvents}, date sets ${s.distinctDateSets}, " +
                    "dates ${if (s.dateSetStable) "STABLE" else "UNSTABLE"}, " +
                    "events ${if (s.eventSetStable) "STABLE" else "UNSTABLE"}, failed runs ${s.failedRuns}" +
                    if (s.unstableDates.isNotEmpty()) ", unstable dates ${s.unstableDates}" else ""
            )
        }

        val metrics = StabilityEvalMetrics(runs, perDocument, modelUsed)
        println(
            "STABILITY: ${metrics.dateStableDocuments}/${metrics.comparableDocuments} documents date-stable, " +
                "${metrics.eventStableDocuments}/${metrics.comparableDocuments} event-stable over $runs runs"
        )
        if (only == null) EvalBaseline.writeCurrent("stability", StabilityEvalMetrics.serializer(), metrics)
    }
})
