package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration test for the decomposition pipeline using SYNTHETIC scenarios.
 *
 * Scenarios are defined as (title, daysUntilDue) pairs so they always represent
 * the right time window regardless of when the test runs. The same four windows
 * covered by [DecompositionScenarioTest] are exercised here with the real AI, to
 * verify that the prompt + orchestrator together produce results within the
 * expected bounds.
 *
 * Structural assertions (step count, daysBeforeDue bounds) are hard — if the AI
 * violates them, the test fails. Content quality (whether steps are sensible) is
 * soft-asserted with a warning print.
 */
class DecompositionIntegrationTest : FunSpec({

    data class Scenario(
        val name: String,
        val eventTitle: String,
        val daysUntilDue: Int,
        val minSteps: Int,
        val maxSteps: Int,
        val description: String,
        val crisisMode: Boolean = false
    )

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val scenarios = listOf(
        Scenario(
            name = "COMFORTABLE/30d",
            eventTitle = "Final Paper: Hidden Systems in American Justice",
            daysUntilDue = 30,
            minSteps = 4,
            maxSteps = 9,
            description = "Major research paper with plenty of lead time"
        ),
        Scenario(
            name = "NORMAL/14d",
            eventTitle = "Issue Brief #1: Secrecy, Identity",
            daysUntilDue = 14,
            minSteps = 3,
            maxSteps = 9,
            description = "Short analytical essay, standard two-week window"
        ),
        Scenario(
            name = "TIGHT/3d",
            eventTitle = "Issue Brief #2: Ethics, Deception, and Undercover Work",
            daysUntilDue = 3,
            minSteps = 2,
            maxSteps = 7,
            description = "Short essay with only 3 days — focus on what is achievable"
        ),
        Scenario(
            name = "CRISIS/today",
            eventTitle = "Issue Brief #3: Connecting Hidden Systems",
            daysUntilDue = 0,
            minSteps = 1,
            maxSteps = 4,
            description = "Due today — should surface extension/partial-credit guidance",
            crisisMode = true
        )
    )

    test("Decomposition respects time-window constraints for all synthetic scenarios").config(
        timeout = (AI_INTEGRATION_TIMEOUT_MS * scenarios.size * 2).milliseconds,
        invocationTimeout = (AI_INTEGRATION_TIMEOUT_MS * scenarios.size * 2).milliseconds
    ) {
        val apiKey = resolveApiKey("DECOMPOSITION INTEGRATION TEST") ?: return@config

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val settings = MapSettings()
        settings.putString("CEF_GEMINI_API_KEY", apiKey)
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, database)
        val orchestrator = DecompositionOrchestrator(aiService, maxDepth = 2)

        println("\n=== Decomposition Integration Test ===")
        println("Today: $today\n")

        val failures = mutableListOf<String>()

        for (scenario in scenarios) {
            val dueDate = today.plus(scenario.daysUntilDue, DateTimeUnit.DAY).toString()
            println("--- [${scenario.name}] ${scenario.eventTitle}")
            println("    Due: $dueDate  (${scenario.daysUntilDue} days)  — ${scenario.description}")

            val subtasks = runCatching {
                skipIfQuotaExhausted("decompose:${scenario.name}") {
                    orchestrator.decompose(scenario.eventTitle, dueDate)
                }
            }.getOrElse { e ->
                println("  ERROR: ${e.message}")
                failures += "[${scenario.name}] ${e.message}"
                continue
            }

            println("  Subtasks (${subtasks.size}):")
            subtasks.forEach { task ->
                println("    [${task.daysBeforeDue}d before] ${task.title}")
            }

            val errors = mutableListOf<String>()

            if (subtasks.size < scenario.minSteps)
                errors += "Expected ≥${scenario.minSteps} steps, got ${subtasks.size}"
            if (subtasks.size > scenario.maxSteps)
                errors += "Expected ≤${scenario.maxSteps} steps, got ${subtasks.size}"

            subtasks.forEach { task ->
                if (task.title.isBlank())
                    errors += "Blank title: $task"
                if (task.daysBeforeDue < 0)
                    errors += "Negative daysBeforeDue (scheduled after due): $task"
                if (task.daysBeforeDue > scenario.daysUntilDue)
                    errors += "daysBeforeDue=${task.daysBeforeDue} exceeds ${scenario.daysUntilDue} days available: ${task.title}"
            }

            // Soft check: crisis scenario should mention extension/late/partial
            if (scenario.crisisMode) {
                val allText = subtasks.joinToString(" ") { it.title + " " + it.description }.lowercase()
                val hasTriage = listOf("extension", "late", "partial", "professor", "submit").any { allText.contains(it) }
                if (!hasTriage) println("  WARN: CRISIS scenario has no extension/late/partial-credit guidance")
            }

            if (errors.isNotEmpty()) {
                failures += "[${scenario.name}]: ${errors.joinToString("; ")}"
                println("  FAIL: ${errors.joinToString("; ")}")
            } else {
                println("  PASS")
            }
        }

        println("\n=== Summary: ${scenarios.size - failures.size}/${scenarios.size} scenarios passed ===")
        if (failures.isNotEmpty()) failures.forEach { println("  • $it") }

        failures.size shouldBe 0
    }
})
