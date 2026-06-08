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

        logger?.d("CriticActor", "First-pass calendar event count: ${firstPass.size}. Entering critique loop...")
        
        val sourceText = fragments.joinToString("\n\n") { it.text }
        var currentEvents = firstPass
        val visitedStates = mutableSetOf<String>()
        visitedStates.add(serializeEvents(firstPass))
        
        var iteration = 1
        val maxIterations = 3
        
        while (iteration <= maxIterations) {
            try {
                val currentJson = serializeEvents(currentEvents)
                val critiquePrompt = AiPrompts.getEventCritiquePrompt(sourceText, currentJson)
                val critiqueResponse = delegate.generateChatResponse(critiquePrompt)

                // If the critique call itself failed (rate-limit etc.), bail gracefully
                if (critiqueResponse.isBlank() || critiqueResponse.startsWith("Error:")) {
                    logger?.e("CriticActor", "Iteration $iteration critique returned an error, exiting loop with last successful state")
                    break
                }

                val correctedEvents = parseEvents(critiqueResponse)

                val correctedJson = serializeEvents(correctedEvents)
                if (visitedStates.contains(correctedJson)) {
                    val isConvergence = (correctedJson == currentJson)
                    if (isConvergence) {
                        logger?.d("CriticActor", "Iteration $iteration: State converged. Exiting critique loop.")
                    } else {
                        logger?.i("CriticActor", "Iteration $iteration: Cycle detected in refinement graph! Exiting critique loop.")
                    }
                    break
                }
                
                logger?.d("CriticActor", "Iteration $iteration: Event list refined (size ${currentEvents.size} -> ${correctedEvents.size}).")
                currentEvents = correctedEvents
                visitedStates.add(correctedJson)
            } catch (e: Exception) {
                logger?.e("CriticActor", "Iteration $iteration critique failed, exiting loop with last successful state", e)
                break
            }
            iteration++
        }
        
        val modified = areEventListsDifferent(firstPass, currentEvents)
        telemetryManager?.logCriticPass(modified)
        logger?.d("CriticActor", "Critique loop finished. Event count: ${firstPass.size} -> ${currentEvents.size} (modified=$modified)")
        return currentEvents
    }

    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        val firstPass = delegate.generateStudyPlan(syllabusText, existingSchedule, preferences)
        if (firstPass.isEmpty()) return firstPass

        logger?.d("CriticActor", "First-pass study plan count: ${firstPass.size}. Entering critique loop...")

        var currentPlan = firstPass
        val visitedStates = mutableSetOf<String>()
        visitedStates.add(serializeEvents(firstPass))
        
        var iteration = 1
        val maxIterations = 3
        
        while (iteration <= maxIterations) {
            try {
                val currentJson = serializeEvents(currentPlan)
                val critiquePrompt = AiPrompts.getEventCritiquePrompt(syllabusText, currentJson)
                val critiqueResponse = delegate.generateChatResponse(critiquePrompt)

                if (critiqueResponse.isBlank() || critiqueResponse.startsWith("Error:")) {
                    logger?.e("CriticActor", "Iteration $iteration study plan critique returned an error, exiting loop with last successful state")
                    break
                }

                val correctedPlan = parseEvents(critiqueResponse)

                val correctedJson = serializeEvents(correctedPlan)
                if (visitedStates.contains(correctedJson)) {
                    val isConvergence = (correctedJson == currentJson)
                    if (isConvergence) {
                        logger?.d("CriticActor", "Iteration $iteration: State converged. Exiting critique loop.")
                    } else {
                        logger?.i("CriticActor", "Iteration $iteration: Cycle detected in refinement graph! Exiting critique loop.")
                    }
                    break
                }
                
                logger?.d("CriticActor", "Iteration $iteration: Study plan refined (size ${currentPlan.size} -> ${correctedPlan.size}).")
                currentPlan = correctedPlan
                visitedStates.add(correctedJson)
            } catch (e: Exception) {
                logger?.e("CriticActor", "Iteration $iteration critique failed, exiting loop with last successful state", e)
                break
            }
            iteration++
        }
        
        val modified = areEventListsDifferent(firstPass, currentPlan)
        telemetryManager?.logCriticPass(modified)
        logger?.d("CriticActor", "Study plan critique loop finished. Event count: ${firstPass.size} -> ${currentPlan.size} (modified=$modified)")
        return currentPlan
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

        logger?.d("CriticActor", "First-pass decomposition count: ${firstPass.size}. Entering critique loop...")
        
        var currentTasks = firstPass
        val visitedStates = mutableSetOf<String>()
        visitedStates.add(serializeTasks(firstPass))
        
        var iteration = 1
        val maxIterations = 3
        
        while (iteration <= maxIterations) {
            try {
                val currentJson = serializeTasks(currentTasks)
                val critiquePrompt = AiPrompts.getDecompositionCritiquePrompt(taskTitle, dueDate, currentJson)
                val critiqueResponse = delegate.generateChatResponse(critiquePrompt)

                if (critiqueResponse.isBlank() || critiqueResponse.startsWith("Error:")) {
                    logger?.e("CriticActor", "Iteration $iteration decomposition critique returned an error, exiting loop with last successful state")
                    break
                }

                val correctedTasks = parseTasks(critiqueResponse)
                
                val correctedJson = serializeTasks(correctedTasks)
                if (visitedStates.contains(correctedJson)) {
                    val isConvergence = (correctedJson == currentJson)
                    if (isConvergence) {
                        logger?.d("CriticActor", "Iteration $iteration: State converged. Exiting critique loop.")
                    } else {
                        logger?.i("CriticActor", "Iteration $iteration: Cycle detected in refinement graph! Exiting critique loop.")
                    }
                    break
                }
                
                logger?.d("CriticActor", "Iteration $iteration: Decomposition refined (size ${currentTasks.size} -> ${correctedTasks.size}).")
                currentTasks = correctedTasks
                visitedStates.add(correctedJson)
            } catch (e: Exception) {
                logger?.e("CriticActor", "Iteration $iteration critique failed, exiting loop with last successful state", e)
                break
            }
            iteration++
        }
        
        logger?.d("CriticActor", "Decomposition critique loop finished. Task count: ${firstPass.size} -> ${currentTasks.size}")
        return currentTasks
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
        return jsonArray.mapNotNull { parseEventFromJson(it) }
    }

    private fun parseEventFromJson(element: JsonElement): Event? {
        return try {
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
            val date = try { kotlinx.datetime.LocalDate.parse(dateStr) }
                       catch (e: Exception) { kotlinx.datetime.LocalDate(2024, 1, 1) }

            if (type == "TIME") {
                val startTimeStr = obj["startTime"]?.jsonPrimitive?.content ?: "09:00"
                val endTimeStr = obj["endTime"]?.jsonPrimitive?.content ?: "10:00"
                val start = try { kotlinx.datetime.LocalTime.parse(startTimeStr) }
                            catch (e: Exception) { kotlinx.datetime.LocalTime(9, 0) }
                val end = try { kotlinx.datetime.LocalTime.parse(endTimeStr) }
                          catch (e: Exception) { kotlinx.datetime.LocalTime(10, 0) }
                TimeEvent(
                    title = title, source = EventSource.AI_GENERATED, category = category,
                    date = date, startTime = start, endTime = end, warning = warning
                )
            } else {
                DayEvent(
                    title = title, source = EventSource.AI_GENERATED,
                    category = category, date = date, warning = warning
                )
            }
        } catch (e: Exception) {
            logger?.e("CriticActor", "Failed to parse event element: ${e.message}")
            null
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
        return jsonArray.mapNotNull { parseTaskFromJson(it) }
    }

    private fun parseTaskFromJson(element: JsonElement): DecomposedTask? {
        return try {
            val obj = element.jsonObject
            val daysBeforeDue = obj["daysBeforeDue"]?.jsonPrimitive?.let {
                it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() ?: 1
            } ?: 1
            DecomposedTask(
                title = obj["title"]?.jsonPrimitive?.content ?: "Sub-task",
                daysBeforeDue = daysBeforeDue,
                description = obj["description"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) {
            logger?.e("CriticActor", "Failed to parse task element: ${e.message}")
            null
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

    private fun areTaskListsDifferent(list1: List<DecomposedTask>, list2: List<DecomposedTask>): Boolean {
        if (list1.size != list2.size) return true
        for (i in list1.indices) {
            val t1 = list1[i]
            val t2 = list2[i]
            if (t1.title != t2.title || t1.daysBeforeDue != t2.daysBeforeDue || t1.description != t2.description) return true
        }
        return false
    }
}
