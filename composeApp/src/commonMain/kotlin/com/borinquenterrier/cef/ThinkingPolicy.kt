package com.borinquenterrier.cef

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Which Gemini model family a model name belongs to, for the purpose of the thinking request field. */
enum class ModelFamily {
    GEMINI_3,
    GEMINI_2_5,

    /** Lite, unknown or never-probed models: no thinking field is sent. */
    OTHER;

    companion object {
        fun of(model: String): ModelFamily = when {
            model.startsWith("gemini-3") -> GEMINI_3
            model.startsWith("gemini-2.5") && "lite" !in model -> GEMINI_2_5
            else -> OTHER
        }
    }
}

/** What the caller wants from the model's reasoning phase. Call sites only ever say this. */
enum class ThinkingEffort {
    /** Leave the model's own default (today's behavior). */
    DEFAULT,

    /** Bounded reasoning, for structured extraction where unbounded thinking only costs latency. */
    LOW
}

/**
 * Maps (model family x effort) to the `generationConfig.thinkingConfig` JSON each family accepts.
 * Every listed cell was confirmed against the live API on 2026-11-08 (docs/ops/thinking-policy.md): 2.5 rejects
 * `thinkingLevel`, 3.8 rejects level `minimal`, and all probed models accept the cells below. An absent cell
 * sends nothing, so an unprobed combination can never trigger a 400.
 */
object ThinkingMatrix {
    private val cells: Map<Pair<ModelFamily, ThinkingEffort>, JsonObject> = mapOf(
        (ModelFamily.GEMINI_3 to ThinkingEffort.LOW) to buildJsonObject { put("thinkingLevel", "low") },
        (ModelFamily.GEMINI_2_5 to ThinkingEffort.LOW) to buildJsonObject { put("thinkingBudget", 1024) }
    )

    fun config(model: String, effort: ThinkingEffort): JsonObject? =
        cells[ModelFamily.of(model) to effort]
}
