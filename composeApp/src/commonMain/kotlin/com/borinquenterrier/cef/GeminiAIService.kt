package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Common implementation for interacting with the Google Gemini API.
 */
class GeminiAIService(
    private val apiKey: String? = null,
    private val accessToken: String? = null
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"


    suspend fun generateCalendarEvents(rawText: String): List<Event> {
        val prompt = AiPrompts.getEventExtractionPrompt(rawText)
        
        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.0) // Deterministic output
        )

        val url = if (apiKey != null) "$baseUrl?key=$apiKey" else baseUrl

        val httpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            if (accessToken != null) {
                header("Authorization", "Bearer $accessToken")
            }
            setBody(request)
        }

        val responseBody = httpResponse.bodyAsText()
        
        if (!httpResponse.status.isSuccess()) {
            throw Exception("Gemini API Error (${httpResponse.status}): $responseBody")
        }

        val response = json.decodeFromString<GeminiResponse>(responseBody)
        val jsonString = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("AI failed to generate a response. Body: $responseBody")

        return parseAiJson(jsonString)
    }

    private fun parseAiJson(jsonString: String): List<Event> {
        return try {
            val cleanedJson = jsonString.trim()
                .removeSurrounding("```json", "```")
                .trim()
            
            val jsonArray = Json.parseToJsonElement(cleanedJson) as JsonArray
            jsonArray.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: "Untitled"
                val type = obj["type"]?.jsonPrimitive?.content ?: "DAY"
                val categoryStr = obj["category"]?.jsonPrimitive?.content ?: "REGULAR"
                val category = try {
                    AcademicCategory.valueOf(categoryStr)
                } catch (e: Exception) {
                    AcademicCategory.REGULAR
                }
                val date = LocalDate.parse(obj["date"]?.jsonPrimitive?.content ?: "2024-01-01")

                if (type == "TIME") {
                    val startTime = LocalTime.parse(obj["startTime"]?.jsonPrimitive?.content ?: "09:00")
                    val endTime = LocalTime.parse(obj["endTime"]?.jsonPrimitive?.content ?: "10:00")
                    TimeEvent(
                        title = title,
                        source = EventSource.AI_GENERATED,
                        category = category,
                        date = date,
                        startTime = startTime,
                        endTime = endTime
                    )
                } else {
                    DayEvent(
                        title = title,
                        source = EventSource.AI_GENERATED,
                        category = category,
                        date = date
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse AI JSON: ${e.message}. Raw string: $jsonString")
        }
    }
}

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class Content(val parts: List<Part>)

@Serializable
data class Part(val text: String)

@Serializable
data class GenerationConfig(val temperature: Double)

@Serializable
data class GeminiResponse(val candidates: List<Candidate>)

@Serializable
data class Candidate(val content: Content)
