package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class ThinkingMatrixTest : FunSpec({

    fun obj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    // One representative model per family. Every (family x effort) pair must be decided here, so adding an
    // enum value without deciding its cell fails this test.
    val representative = mapOf(
        ModelFamily.GEMINI_3 to "gemini-3.8-flash",
        ModelFamily.GEMINI_2_5 to "gemini-2.5-flash",
        ModelFamily.OTHER to "gemini-2.5-flash-lite"
    )
    val expected: Map<Pair<ModelFamily, ThinkingEffort>, JsonObject?> = mapOf(
        (ModelFamily.GEMINI_3 to ThinkingEffort.DEFAULT) to null,
        (ModelFamily.GEMINI_3 to ThinkingEffort.LOW) to obj { put("thinkingLevel", "low") },
        (ModelFamily.GEMINI_2_5 to ThinkingEffort.DEFAULT) to null,
        (ModelFamily.GEMINI_2_5 to ThinkingEffort.LOW) to obj { put("thinkingBudget", 1024) },
        (ModelFamily.OTHER to ThinkingEffort.DEFAULT) to null,
        (ModelFamily.OTHER to ThinkingEffort.LOW) to null
    )

    test("every family x effort pair is decided and matches the probed encoding") {
        for (family in ModelFamily.entries) for (effort in ThinkingEffort.entries) {
            val key = family to effort
            check(key in expected) { "No expectation for $key" }
            ThinkingMatrix.config(representative.getValue(family), effort) shouldBe expected[key]
        }
    }

    test("only probed models get a family; unprobed siblings and unknown names get none") {
        ModelFamily.of("gemini-3.8-flash") shouldBe ModelFamily.GEMINI_3
        ModelFamily.of("gemini-3.6-flash") shouldBe ModelFamily.GEMINI_3
        ModelFamily.of("gemini-2.5-flash") shouldBe ModelFamily.GEMINI_2_5
        // Unprobed: a request field they reject would 400, burn 5 retries and blacklist the model.
        ModelFamily.of("gemini-3.5-flash") shouldBe ModelFamily.OTHER
        ModelFamily.of("gemini-3.5-flash-lite") shouldBe ModelFamily.OTHER
        ModelFamily.of("gemini-2.5-pro") shouldBe ModelFamily.OTHER
        ModelFamily.of("gemini-2.5-flash-lite") shouldBe ModelFamily.OTHER
        ModelFamily.of("gemini-flash-latest") shouldBe ModelFamily.OTHER
        ModelFamily.of("gemini-3.9-flash") shouldBe ModelFamily.OTHER
        ThinkingMatrix.config("gemini-3.5-flash", ThinkingEffort.LOW) shouldBe null
        ThinkingMatrix.config("gemini-2.5-pro", ThinkingEffort.LOW) shouldBe null
    }

    test("request body carries thinkingConfig only when the model and effort define a cell") {
        val withThinking = GeminiBodyBuilder.buildJsonRequestBody(
            "p", model = "gemini-3.8-flash", thinking = ThinkingEffort.LOW
        )["generationConfig"]!!.jsonObject
        withThinking["thinkingConfig"] shouldBe obj { put("thinkingLevel", "low") }

        val defaultEffort = GeminiBodyBuilder.buildJsonRequestBody(
            "p", model = "gemini-3.8-flash"
        )["generationConfig"]!!.jsonObject
        defaultEffort.containsKey("thinkingConfig") shouldBe false

        val noModel = GeminiBodyBuilder.buildJsonRequestBody("p", thinking = ThinkingEffort.LOW)["generationConfig"]!!.jsonObject
        noModel.containsKey("thinkingConfig") shouldBe false
    }
})
