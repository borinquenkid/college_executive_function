package com.borinquenterrier.cef

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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

    /**
     * Build a request body with an inline document/image part ([base64Data] of [mimeType]) plus a
     * text instruction — used to OCR an image-only PDF via Gemini's native document vision.
     */
    fun buildDocumentRequestBody(
        prompt: String,
        base64Data: String,
        mimeType: String,
        temperature: Double = 0.0
    ): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                putJsonArray("parts") {
                    addJsonObject {
                        putJsonObject("inlineData") {
                            put("mimeType", mimeType)
                            put("data", base64Data)
                        }
                    }
                    addJsonObject { put("text", prompt) }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", temperature)
        }
    }
}
