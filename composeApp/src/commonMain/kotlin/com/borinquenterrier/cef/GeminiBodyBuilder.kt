package com.borinquenterrier.cef

import kotlinx.serialization.json.*

/**
 * Encapsulates Gemini API request body construction.
 * Reduces method complexity in GeminiAIService.
 */
object GeminiBodyBuilder {
    /**
     * Build a standard Gemini API request body with JSON schema support.
     */
    fun buildJsonRequestBody(
        prompt: String,
        temperature: Double = 0.0,
        responseMimeType: String? = "application/json"
    ): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                }
            }
        }
        putJsonObject("generationConfig") {
            if (responseMimeType == "application/json") {
                put("responseMimeType", responseMimeType)
            }
            put("temperature", temperature)
        }
    }

    /**
     * Build a Gemini API request body for text (non-JSON) responses.
     */
    fun buildTextRequestBody(
        prompt: String,
        temperature: Double = 0.0
    ): JsonObject = buildJsonRequestBody(prompt, temperature, responseMimeType = null)
}
