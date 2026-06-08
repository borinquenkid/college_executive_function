package com.borinquenterrier.cef

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Asks the AI to audit a syllabus's text for ambiguities and returns a list of
 * human-readable warning strings — empty if the syllabus is unambiguous or the
 * AI's response could not be parsed.
 */
class SyllabusAuditor(
    private val aiService: AIService,
    private val logger: Logger? = null
) {
    private val tag = "SyllabusAuditor"

    suspend fun audit(text: String): List<String> {
        val prompt = AiPrompts.getSyllabusAuditPrompt(text)
        val response = aiService.generateChatResponse(prompt)
        return try {
            val cleanJson = response.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            val root = Json.parseToJsonElement(cleanJson).jsonObject
            val hasAmbiguities = root["hasAmbiguities"]?.jsonPrimitive?.booleanOrNull ?: false
            if (hasAmbiguities) {
                root["findings"]?.jsonArray?.map { element ->
                    val obj = element.jsonObject
                    val desc = obj["description"]?.jsonPrimitive?.content ?: "Syllabus ambiguity detected"
                    val type = obj["type"]?.jsonPrimitive?.content ?: "GENERAL"
                    "[$type] $desc"
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger?.e(tag, "Failed to parse syllabus audit response: $response", e)
            emptyList()
        }
    }
}
