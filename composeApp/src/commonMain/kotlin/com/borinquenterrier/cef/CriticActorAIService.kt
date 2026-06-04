package com.borinquenterrier.cef

import kotlinx.serialization.json.*

class CriticActorAIService(
    private val delegate: AIService,
    private val logger: Logger? = null,
    private val telemetryManager: TelemetryManager? = null
) : AIService by delegate {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val firstPass = delegate.generateCalendarEvents(fragments)
        if (firstPass.isEmpty()) return firstPass

        logger?.d("CriticActor", "First-pass calendar event count: ${firstPass.size}. Launching critique pass...")
        
        try {
            val sourceText = fragments.joinToString("\n\n") { it.text }
            val firstPassJson = serializeEvents(firstPass)
            val critiquePrompt = AiPrompts.getEventCritiquePrompt(sourceText, firstPassJson)
            val critiqueResponse = delegate.generateChatResponse(critiquePrompt)
            val correctedEvents = parseEvents(critiqueResponse)
            
            val modified = areEventListsDifferent(firstPass, correctedEvents)
            telemetryManager?.logCriticPass(modified)
            
            logger?.d("CriticActor", "Critique complete. Event count: ${firstPass.size} -> ${correctedEvents.size} (modified=$modified)")
            return correctedEvents
        } catch (e: Exception) {
            logger?.e("CriticActor", "Critique failed, falling back to first pass events", e)
            telemetryManager?.logCriticPass(false)
            return firstPass
        }
    }

    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        val firstPass = delegate.generateStudyPlan(syllabusText, existingSchedule, preferences)
        if (firstPass.isEmpty()) return firstPass

        logger?.d("CriticActor", "First-pass study plan count: ${firstPass.size}. Launching critique pass...")
        
        try {
            val firstPassJson = serializeEvents(firstPass)
            val critiquePrompt = AiPrompts.getEventCritiquePrompt(syllabusText, firstPassJson)
            val critiqueResponse = delegate.generateChatResponse(critiquePrompt)
            val correctedEvents = parseEvents(critiqueResponse)
            
            val modified = areEventListsDifferent(firstPass, correctedEvents)
            telemetryManager?.logCriticPass(modified)
            
            logger?.d("CriticActor", "Study plan critique complete. Event count: ${firstPass.size} -> ${correctedEvents.size} (modified=$modified)")
            return correctedEvents
        } catch (e: Exception) {
            logger?.e("CriticActor", "Study plan critique failed, falling back to first pass", e)
            telemetryManager?.logCriticPass(false)
            return firstPass
        }
    }

    override suspend fun generateChatResponse(prompt: String): String {
        val firstPass = delegate.generateChatResponse(prompt)
        if (firstPass.isBlank() || firstPass.startsWith("Error:")) return firstPass

        // If the prompt is a critique prompt itself, do not critique it (prevent infinite recursion!)
        if (prompt.contains("You are a strict data auditor") || 
            prompt.contains("You are a factual critique") || 
            prompt.contains("You are an executive function coach and quality auditor")) {
            return firstPass
        }

        logger?.d("CriticActor", "First-pass chat response generated. Launching critique pass...")
        
        try {
            val critiquePrompt = AiPrompts.getChatCritiquePrompt(prompt, firstPass)
            val critiqueResponse = delegate.generateChatResponse(critiquePrompt)
            logger?.d("CriticActor", "Chat critique complete.")
            return critiqueResponse
        } catch (e: Exception) {
            logger?.e("CriticActor", "Chat critique failed, falling back to first pass response", e)
            return firstPass
        }
    }

    override suspend fun decomposeTask(taskTitle: String, dueDate: String): List<DecomposedTask> {
        val firstPass = delegate.decomposeTask(taskTitle, dueDate)
        if (firstPass.isEmpty()) return firstPass

        logger?.d("CriticActor", "First-pass decomposition count: ${firstPass.size}. Launching critique pass...")
        
        try {
            val firstPassJson = serializeTasks(firstPass)
            val critiquePrompt = AiPrompts.getDecompositionCritiquePrompt(taskTitle, dueDate, firstPassJson)
            val critiqueResponse = delegate.generateChatResponse(critiquePrompt)
            val correctedTasks = parseTasks(critiqueResponse)
            logger?.d("CriticActor", "Decomposition critique complete. Task count: ${firstPass.size} -> ${correctedTasks.size}")
            return correctedTasks
        } catch (e: Exception) {
            logger?.e("CriticActor", "Decomposition critique failed, falling back to first pass", e)
            return firstPass
        }
    }

    private fun serializeEvents(events: List<Event>): String {
        return buildJsonArray {
            events.forEach { event ->
                addJsonObject {
                    put("title", event.title)
                    put("type", if (event is TimeEvent) "TIME" else "DAY")
                    put("category", event.category.name)
                    put("date", event.date.toString())
                    if (event is TimeEvent) {
                        put("startTime", event.startTime.toString())
                        put("endTime", event.endTime.toString())
                    }
                    if (event.warning != null) {
                        put("warning", event.warning)
                    }
                }
            }
        }.toString()
    }

    private fun parseEvents(jsonText: String): List<Event> {
        val cleanJson = jsonText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        val root = json.parseToJsonElement(cleanJson)
        val jsonArray = if (root is JsonArray) {
            root
        } else if (root is JsonObject && root.containsKey("events")) {
            root["events"]!!.jsonArray
        } else {
            throw Exception("Unexpected JSON structure: $cleanJson")
        }
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: "Untitled Event"
            val type = obj["type"]?.jsonPrimitive?.content ?: "DAY"
            val dateStr = obj["date"]?.jsonPrimitive?.content ?: "2024-01-01"
            val categoryStr = obj["category"]?.jsonPrimitive?.content ?: "REGULAR"
            val warning = obj["warning"]?.jsonPrimitive?.content
            val category = try {
                AcademicCategory.valueOf(categoryStr)
            } catch (e: Exception) {
                AcademicCategory.REGULAR
            }

            val date = try { kotlinx.datetime.LocalDate.parse(dateStr) } catch (e: Exception) { kotlinx.datetime.LocalDate(2024,1,1) }

            if (type == "TIME") {
                val startTimeStr = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                val endTimeStr = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                val start = try { kotlinx.datetime.LocalTime.parse(startTimeStr) } catch (e: Exception) { kotlinx.datetime.LocalTime(9,0) }
                val end = try { kotlinx.datetime.LocalTime.parse(endTimeStr) } catch (e: Exception) { kotlinx.datetime.LocalTime(10,0) }
                
                TimeEvent(
                    title = title,
                    source = EventSource.AI_GENERATED,
                    category = category,
                    date = date,
                    startTime = start,
                    endTime = end,
                    warning = warning
                )
            } else {
                DayEvent(
                    title = title,
                    source = EventSource.AI_GENERATED,
                    category = category,
                    date = date,
                    warning = warning
                )
            }
        }
    }

    private fun serializeTasks(tasks: List<DecomposedTask>): String {
        return buildJsonArray {
            tasks.forEach { task ->
                addJsonObject {
                    put("title", task.title)
                    put("daysBeforeDue", task.daysBeforeDue)
                    put("description", task.description)
                }
            }
        }.toString()
    }

    private fun parseTasks(jsonText: String): List<DecomposedTask> {
        val cleanJson = jsonText.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        val root = json.parseToJsonElement(cleanJson)
        val jsonArray = when {
            root is JsonArray -> root
            root is JsonObject && root.containsKey("tasks") -> root["tasks"]!!.jsonArray
            else -> throw Exception("Unexpected JSON structure: $cleanJson")
        }
        return jsonArray.map { element ->
            val obj = element.jsonObject
            val daysBeforeDue = obj["daysBeforeDue"]?.jsonPrimitive?.let {
                it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() ?: 1
            } ?: 1
            DecomposedTask(
                title = obj["title"]?.jsonPrimitive?.content ?: "Sub-task",
                daysBeforeDue = daysBeforeDue,
                description = obj["description"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    private fun areEventListsDifferent(list1: List<Event>, list2: List<Event>): Boolean {
        if (list1.size != list2.size) return true
        for (i in list1.indices) {
            val e1 = list1[i]
            val e2 = list2[i]
            if (e1.title != e2.title || e1.date != e2.date || e1.category != e2.category) return true
            if (e1 is TimeEvent && e2 is TimeEvent) {
                if (e1.startTime != e2.startTime || e1.endTime != e2.endTime) return true
            } else if (e1 is TimeEvent || e2 is TimeEvent) {
                return true
            }
        }
        return false
    }
}
