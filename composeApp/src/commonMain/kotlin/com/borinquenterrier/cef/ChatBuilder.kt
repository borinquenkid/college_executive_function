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
        question: String
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

        return """
            You are an Academic Success Assistant with full access to a student's course materials.
            Answer questions by reasoning across ALL provided sources and synthesizing information.

            # Course Materials (${sourceBlocks.size} source(s))

            $sourcesSection

            # Prior Conversation
            $historySection

            # Student's Question
            $question

            # Instructions
            - Base your answer ONLY on the provided materials. Do not use outside knowledge.
            - If relevant information spans multiple sources, synthesize it and cite the source title.
            - If the answer is not found in any provided source, say so clearly rather than guessing.
            - Keep answers concise and actionable for a student managing their academics.
        """.trimIndent()
    }

    fun getChatCritiquePrompt(originalPrompt: String, response: String): String {
        return """
            You are a factual critique and quality control agent.
            
            Below is the original user prompt / chat history and the generated response.
            
            # Original Prompt / Context:
            $originalPrompt
            
            # Generated Response:
            $response
            
            # Task:
            Critique the generated response. Check if:
            1. The response contains any assertions or facts that are NOT supported by the source materials in the original prompt context.
            2. The response contains hallucinations, fabrications, or outside assumptions.
            
            If the response is fully factual and supported by the sources, return the original response completely unchanged.
            If the response contains unsupported information or makes assumptions, revise it to ONLY use facts explicitly stated in the source materials. If a fact cannot be verified, clearly state "I do not have enough information to answer that based on the provided materials."
            
            Return ONLY the final revised response text. Do not add any intros, explanations, or meta-commentary.
        """.trimIndent()
    }
}
