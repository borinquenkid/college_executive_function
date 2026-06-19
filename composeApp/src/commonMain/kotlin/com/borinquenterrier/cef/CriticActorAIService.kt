package com.borinquenterrier.cef

class CriticActorAIService(
    private val delegate: AIService,
    private val logger: Logger? = null,
    private val telemetryManager: TelemetryManager? = null
) : AIService by delegate {

    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        return AppTracer.current.span("critic.calendar_events") {
            val firstPass = delegate.generateCalendarEvents(fragments)
            setAttribute("events.after_extraction", firstPass.size.toLong())
            setAttribute("class.after_extraction",
                firstPass.count { it.category == AcademicCategory.CLASS }.toLong())

            if (firstPass.isEmpty()) return@span firstPass

            val sourceText = fragments.joinToString("\n\n") { it.text }
            val refined = runCritiqueLoop(
                logLabel = "calendar event",
                firstPass = firstPass,
                serialize = CriticJsonCodec::serializeEvents,
                parse = { CriticJsonCodec.parseEvents(it, logger) },
                buildPrompt = { currentJson -> AiPrompts.getEventCritiquePrompt(sourceText, currentJson) }
            )

            val modified = areEventListsDifferent(firstPass, refined)
            telemetryManager?.logCriticPass(modified)
            setAttribute("events.after_critique", refined.size.toLong())
            setAttribute("class.after_critique",
                refined.count { it.category == AcademicCategory.CLASS }.toLong())
            setAttribute("class.dropped_by_critique",
                (firstPass.count { it.category == AcademicCategory.CLASS } -
                 refined.count { it.category == AcademicCategory.CLASS }).toLong())
            setAttribute("critique.modified", modified.toString())
            logger?.d(
                "CriticActor",
                "Critique loop finished. Event count: ${firstPass.size} -> ${refined.size} (modified=$modified)"
            )
            refined
        }
    }

    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        val firstPass = delegate.generateStudyPlan(syllabusText, existingSchedule, preferences)
        if (firstPass.isEmpty()) return firstPass

        val refined = runCritiqueLoop(
            logLabel = "study plan",
            firstPass = firstPass,
            serialize = CriticJsonCodec::serializeEvents,
            parse = { CriticJsonCodec.parseEvents(it, logger) },
            buildPrompt = { currentJson ->
                StudyPlanBuilder.getStudyPlanCritiquePrompt(syllabusText, currentJson)
            }
        )

        val modified = areEventListsDifferent(firstPass, refined)
        telemetryManager?.logCriticPass(modified)
        logger?.d(
            "CriticActor",
            "Study plan critique loop finished. Event count: ${firstPass.size} -> ${refined.size} (modified=$modified)"
        )
        return refined
    }

    private suspend fun <T> runCritiqueLoop(
        logLabel: String,
        firstPass: List<T>,
        serialize: (List<T>) -> String,
        parse: (String) -> List<T>,
        buildPrompt: (currentJson: String) -> String
    ): List<T> {
        logger?.d(
            "CriticActor",
            "First-pass $logLabel count: ${firstPass.size}. Entering critique loop..."
        )

        var current = firstPass
        val visitedStates = mutableSetOf<String>()
        visitedStates.add(serialize(firstPass))

        var iteration = 1
        val maxIterations = 3

        while (iteration <= maxIterations) {
            try {
                val currentJson = serialize(current)
                val critiqueResponse = delegate.generateChatResponse(buildPrompt(currentJson))

                if (critiqueResponse.isBlank() || critiqueResponse.startsWith("Error:")) {
                    logger?.e(
                        "CriticActor",
                        "Iteration $iteration $logLabel critique returned an error, exiting loop with last successful state"
                    )
                    break
                }

                val corrected = parse(critiqueResponse)
                val correctedJson = serialize(corrected)
                if (visitedStates.contains(correctedJson)) {
                    if (correctedJson == currentJson) {
                        logger?.d(
                            "CriticActor",
                            "Iteration $iteration: State converged. Exiting critique loop."
                        )
                    } else {
                        logger?.i(
                            "CriticActor",
                            "Iteration $iteration: Cycle detected in refinement graph! Exiting critique loop."
                        )
                    }
                    break
                }

                logger?.d(
                    "CriticActor",
                    "Iteration $iteration: $logLabel refined (size ${current.size} -> ${corrected.size})."
                )
                current = corrected
                visitedStates.add(correctedJson)
            } catch (e: Exception) {
                logger?.e(
                    "CriticActor",
                    "Iteration $iteration critique failed, exiting loop with last successful state",
                    e
                )
                break
            }
            iteration++
        }

        return current
    }

    override suspend fun generateChatResponse(prompt: String): String {
        val firstPass = delegate.generateChatResponse(prompt)
        if (firstPass.isBlank() || firstPass.startsWith("Error:")) return firstPass

        // If the prompt is a critique prompt itself, do not critique it (prevent infinite recursion!)
        if (prompt.contains("You are a strict data auditor") ||
            prompt.contains("You are a factual critique") ||
            prompt.contains("You are an executive function coach and quality auditor")
        ) {
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

        val refined = runCritiqueLoop(
            logLabel = "decomposition",
            firstPass = firstPass,
            serialize = CriticJsonCodec::serializeTasks,
            parse = { CriticJsonCodec.parseTasks(it, logger) },
            buildPrompt = { currentJson ->
                AiPrompts.getDecompositionCritiquePrompt(
                    taskTitle,
                    dueDate,
                    currentJson
                )
            }
        )

        logger?.d(
            "CriticActor",
            "Decomposition critique loop finished. Task count: ${firstPass.size} -> ${refined.size}"
        )
        return refined
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
