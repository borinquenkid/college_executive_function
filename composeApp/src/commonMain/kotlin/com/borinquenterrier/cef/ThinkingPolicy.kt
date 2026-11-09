package com.borinquenterrier.cef

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Which thinking-request family a model belongs to. Only models that were probed against the live API are
 * listed (docs/ops/thinking-policy.md, 2026-11-08); every other name, including future or unprobed siblings such
 * as gemini-3.5-flash or gemini-2.5-pro, is [OTHER] and gets no thinking field. Matching is by exact name on
 * purpose: a request field a model rejects returns a 400 that the executor retries 5 times on the same model,
 * then blacklists it for an hour and fails the extraction, so an unprobed model must never be sent one.
 */
enum class ModelFamily {
    GEMINI_3,
    GEMINI_2_5,

    /** Unprobed, lite or unknown models: no thinking field is sent. */
    OTHER;

    companion object {
        /** To support another model: probe it, add it here, and add its cell to ThinkingMatrixTest. */
        private val probed: Map<String, ModelFamily> = mapOf(
            "gemini-3.8-flash" to GEMINI_3,
            "gemini-3.6-flash" to GEMINI_3,
            "gemini-2.5-flash" to GEMINI_2_5
        )

        fun of(model: String): ModelFamily = probed[model] ?: OTHER
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
 * `thinkingLevel`, 3.8 rejects level `minimal`, and the probed models accept the cells below. An absent cell, or
 * a model not in [ModelFamily]'s probed list, sends nothing, so it cannot trigger a 400.
 */
object ThinkingMatrix {
    private val cells: Map<Pair<ModelFamily, ThinkingEffort>, JsonObject> = mapOf(
        (ModelFamily.GEMINI_3 to ThinkingEffort.LOW) to buildJsonObject { put("thinkingLevel", "low") },
        (ModelFamily.GEMINI_2_5 to ThinkingEffort.LOW) to buildJsonObject { put("thinkingBudget", 1024) }
    )

    fun config(model: String, effort: ThinkingEffort): JsonObject? =
        cells[ModelFamily.of(model) to effort]
}
