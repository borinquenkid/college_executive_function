package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * ADR 0004 / ROADMAP Phase 13, XM-5. `CEF_GEMINI_API_KEY` is a CI-test-only secret (see AGENTS.md's
 * "AI Eval Corpus Gate" section) — it must never be reachable from the cross-term memory path,
 * which runs against real student data in production. Today's aggregation/trigger/repository code
 * makes no LLM call at all (ADR 0004: plain aggregation only), so the strongest regression
 * guardrail is structural: fail loudly the moment either forbidden reference appears in these
 * files, rather than trusting a future editor to remember the rule.
 *
 * If a future qualitative-distillation LLM call is added here, it must go through the same
 * `AIService` key resolution `ContextAgent.generateChatResponse` already uses (per-tenant/
 * per-device `Settings` key) — this test's `AIService` reference itself is intentionally exempt
 * only in this docstring, not in the scanned files below.
 */
class TermProfileKeySourceGuardrailTest : FunSpec({

    val scannedFiles = listOf(
        "TermProfileAggregator.kt",
        "TermBoundaryTrigger.kt",
        "TermProfileRepository.kt"
    )

    fun findCommonMainFile(name: String): File = listOf(
        File("src/commonMain/kotlin/com/borinquenterrier/cef/$name"),
        File("composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/$name"),
        File("../composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/$name")
    ).first { it.exists() }

    scannedFiles.forEach { fileName ->
        test("$fileName never references CEF_GEMINI_API_KEY") {
            val text = findCommonMainFile(fileName).readText()
            text.contains("CEF_GEMINI_API_KEY") shouldBe false
        }

        test("$fileName never references AIService (no LLM call in this path today)") {
            val text = findCommonMainFile(fileName).readText()
            text.contains("AIService") shouldBe false
        }
    }
})
