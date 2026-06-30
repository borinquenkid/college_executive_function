package com.borinquenterrier.cef

/**
 * Builder for multi-source chat and critique prompts.
 * Handles conversation context aggregation and response quality assurance.
 */
object ChatBuilder {

    private const val MAX_CHARS_PER_SOURCE = 6_000
    private const val MAX_HISTORY_TURNS = 10

    fun getMultiSourceChatPrompt(
        sourceBlocks: List<SourceContextBlock>,
        conversationHistory: List<Pair<String, String>>,
        question: String,
        warnings: List<String> = emptyList()
    ): String {
        val sourcesSection = if (sourceBlocks.isEmpty()) {
            "No course materials are loaded yet. Ask the student to add a source first."
        } else {
            sourceBlocks.joinToString("\n\n---\n\n") { block ->
                buildString {
                    appendLine("### ${block.title} [${block.category}]")
                    if (!block.metadata.isNullOrBlank()) {
                        appendLine("**Policies & Rules:**")
                        appendLine(block.metadata)
                        appendLine()
                    }
                    val content = if (block.fragmentText.length > MAX_CHARS_PER_SOURCE)
                        block.fragmentText.take(MAX_CHARS_PER_SOURCE) + "\n… [content truncated]"
                    else block.fragmentText
                    appendLine("**Content:**")
                    append(content)
                }
            }
        }

        val historySection = if (conversationHistory.isEmpty()) {
            "(No prior messages)"
        } else {
            conversationHistory.takeLast(MAX_HISTORY_TURNS)
                .joinToString("\n") { (author, content) ->
                    "${if (author == "User") "Student" else "Assistant"}: $content"
                }
        }

        val warningsSection = if (warnings.isEmpty()) "" else buildString {
            appendLine()
            appendLine("The following issues were flagged during ingestion and may require follow-up:")
            warnings.forEach { appendLine("- $it") }
        }

        return """
            # MEMORANDUM BRIEF: MULTI-SOURCE CHAT CONTEXT

            ## 1. TOPIC CLARIFICATION
            You are acting as an Academic Success Assistant. This brief instructs you to answer student academic questions based strictly on their loaded course materials and prior conversation history.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <course_materials>
            $sourcesSection
            </course_materials>
            ${if (warningsSection.isNotBlank()) "\n<source_warnings>\n$warningsSection\n</source_warnings>\n" else ""}
            <conversation_history>
            $historySection
            </conversation_history>

            ## 3. TASK PROMPT
            Analyze the materials inside <course_materials> and the dialog history inside <conversation_history>. 
            Then, formulate a clear, helpful response to the student's question below:
            
            Student's Question:
            $question

            ## 4. CONSTRAINTS & GUARDRAILS
            - Base your answer ONLY on the provided materials in <course_materials>. Do not use outside knowledge or make ungrounded assumptions.
            - If the answer is not found in the provided sources, say so clearly rather than guessing.
            - If relevant information spans multiple sources, synthesize it and explicitly cite the source titles.
            - Keep answers concise, direct, and actionable for a student.
        """.trimIndent()
    }

    fun getChatCritiquePrompt(originalPrompt: String, response: String): String {
        return """
            # MEMORANDUM BRIEF: CHAT RESPONSE QUALITY AUDIT

            ## 1. TOPIC CLARIFICATION
            This brief instructs you to act as a factual critique and quality control agent to review and correct a generated response against its original prompt context.

            ## 2. STRUCTURED REFERENCE MATERIAL
            <original_prompt_context>
            $originalPrompt
            </original_prompt_context>

            <generated_chat_response>
            $response
            </generated_chat_response>

            ## 3. TASK PROMPT
            Audit the response inside <generated_chat_response> against the source context and instructions in <original_prompt_context>.
            
            Identify and correct any:
            1. Assertions or facts that are NOT supported by the source materials in the original context.
            2. Hallucinations, fabrications, or outside assumptions.

            ## 4. CONSTRAINTS & GUARDRAILS
            - Return ONLY the final revised response text.
            - Do NOT include any intros, explanations, markdown code blocks (do not wrap in ```json or ```), or meta-commentary.
            - If the response is fully factual and supported by the sources, return the original response completely unchanged.
            - If a fact cannot be verified, clearly state "I do not have enough information to answer that based on the provided materials."
        """.trimIndent()
    }
}

