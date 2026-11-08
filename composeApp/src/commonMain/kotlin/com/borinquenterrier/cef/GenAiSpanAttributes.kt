package com.borinquenterrier.cef

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * OpenTelemetry GenAI semantic-convention attributes for a Gemini call span (T7).
 * Names follow the `gen_ai.*` registry: operation, provider, request model, and token usage.
 * Latency is the span's own duration; whether the grounding gate fired is already recorded on the
 * `grounding.*` spans, so neither is duplicated here.
 *
 * Pure and tolerant: a body that is not JSON or has no `usageMetadata` simply yields no token attributes
 * (telemetry must never fail the request).
 */
object GenAiSpanAttributes {
    private val json = Json { ignoreUnknownKeys = true }

    fun apply(scope: SpanScope, modelName: String, responseBody: String) {
        scope.setAttribute("gen_ai.operation.name", "generate_content")
        scope.setAttribute("gen_ai.provider.name", "gcp.gemini")
        scope.setAttribute("gen_ai.request.model", modelName)
        val usage = usageMetadata(responseBody) ?: return
        usage["promptTokenCount"]?.jsonPrimitive?.longOrNull
            ?.let { scope.setAttribute("gen_ai.usage.input_tokens", it) }
        usage["candidatesTokenCount"]?.jsonPrimitive?.longOrNull
            ?.let { scope.setAttribute("gen_ai.usage.output_tokens", it) }
    }

    private fun usageMetadata(responseBody: String): JsonObject? = try {
        json.parseToJsonElement(responseBody).jsonObject["usageMetadata"]?.jsonObject
    } catch (_: Exception) {
        null // not JSON / unexpected shape: no token attributes
    }
}
