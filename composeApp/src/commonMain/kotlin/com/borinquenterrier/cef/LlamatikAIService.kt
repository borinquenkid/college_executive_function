package com.borinquenterrier.cef

import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.*

class LlamatikAIService(
    modelPath: String,
    private val logger: Logger? = null,
) {
    private val tag = "LlamatikAI"
    private val json = Json { ignoreUnknownKeys = true }

    init {
        try {
            // Configure parameters before initialization to avoid GGML_ASSERT and handle larger contexts
            LlamaBridge.updateGenerateParams(
                temperature = 0.0f,
                maxTokens = 2048,
                topP = 0.95f,
                topK = 40,
                repeatPenalty = 1.1f,
                contextLength = 8192,
                numThreads = 4,
                useMmap = true,
                flashAttention = false,
                batchSize = 4096
            )
            LlamaBridge.initGenerateModel(modelPath)
            logger?.d(tag, "Llamatik initialized with model: $modelPath")
        } catch (e: Exception) {
            logger?.e(tag, "Failed to initialize Llamatik: ${e.message}")
        }
    }

    suspend fun generateCalendarEvents(parts: List<SourcePart>): List<Event> {
        val combinedJson = buildJsonArray {
            parts.forEach { part ->
                add(Json.parseToJsonElement(part.toJson()))
            }
        }.toString()
        val prompt = AiPrompts.getSourceEventExtractionPrompt(combinedJson)
        return generateEventsFromRawPrompt(prompt)
    }

    suspend fun generateEventsFromRawPrompt(prompt: String): List<Event> = withContext(Dispatchers.Default) {
        try {
            // Passing empty schema "" tells Llamatik to generate raw text without grammar constraints.
            // This is a diagnostic step to isolate the native SIGABRT.
            val response = LlamaBridge.generateJson(prompt, "")
            parseAiJson(response)
        } catch (e: Exception) {
            logger?.e(tag, "Llamatik generation failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseAiJson(jsonString: String): List<Event> {
        return try {
            // Find the first '[' and last ']' to extract the JSON array from potentially messy LLM output
            val start = jsonString.indexOf('[')
            val end = jsonString.lastIndexOf(']')
            if (start == -1 || end == -1) {
                logger?.e(tag, "No JSON array found in output: $jsonString")
                return emptyList()
            }
            val cleanedJson = jsonString.substring(start, end + 1)
            
            val jsonArray = json.parseToJsonElement(cleanedJson).jsonArray
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
                val dateStr = obj["date"]?.jsonPrimitive?.content ?: "2024-01-01"
                val date = try { LocalDate.parse(dateStr) } catch(e: Exception) { LocalDate(2024, 1, 1) }

                if (type == "TIME") {
                    val startTime = try { LocalTime.parse(obj["startTime"]?.jsonPrimitive?.content ?: "09:00") } catch(e: Exception) { LocalTime(9, 0) }
                    val endTime = try { LocalTime.parse(obj["endTime"]?.jsonPrimitive?.content ?: "10:00") } catch(e: Exception) { LocalTime(10, 0) }
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
            logger?.e(tag, "Failed to parse Llamatik JSON: ${e.message}")
            emptyList()
        }
    }
}
