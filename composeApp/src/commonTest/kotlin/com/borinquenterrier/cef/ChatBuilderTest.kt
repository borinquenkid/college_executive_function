package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain

class ChatBuilderTest : StringSpec({

    "getMultiSourceChatPrompt should handle empty source blocks" {
        val result = ChatBuilder.getMultiSourceChatPrompt(
            emptyList(),
            emptyList(),
            "What's the grading scale?"
        )

        result.shouldContain("No course materials are loaded yet")
        result.shouldNotBeBlank()
    }

    "getMultiSourceChatPrompt should include source blocks with metadata" {
        val sources = listOf(
            SourceContextBlock(
                "CS101 Syllabus",
                "SYLLABUS",
                "Final 30%, Quizzes 20%",
                "Course content here"
            )
        )
        val result =
            ChatBuilder.getMultiSourceChatPrompt(sources, emptyList(), "When is the final?")

        result.shouldContain("CS101 Syllabus")
        result.shouldContain("SYLLABUS")
        result.shouldContain("Policies & Rules")
        result.shouldContain("Final 30%, Quizzes 20%")
        result.shouldContain("Content")
        result.shouldContain("Course content here")
    }

    "getMultiSourceChatPrompt should include conversation history" {
        val sources = listOf(
            SourceContextBlock("Syllabus", "SYLLABUS", null, "Course info")
        )
        val history = listOf(
            "User" to "When is the midterm?",
            "Assistant" to "March 15, 2024"
        )
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, history, "When is the final?")

        result.shouldContain("conversation_history")
        result.shouldContain("Student: When is the midterm?")
        result.shouldContain("Assistant: March 15, 2024")
    }

    "getMultiSourceChatPrompt should show (No prior messages) when history is empty" {
        val sources = listOf(
            SourceContextBlock("Syllabus", "SYLLABUS", null, "Content")
        )
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, emptyList(), "Question?")

        result.shouldContain("(No prior messages)")
    }

    "getMultiSourceChatPrompt should truncate long fragment text" {
        val longText = "x".repeat(10000)
        val sources = listOf(
            SourceContextBlock("Big Syllabus", "SYLLABUS", null, longText)
        )
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, emptyList(), "How long is this?")

        result.shouldContain("[content truncated]")
    }

    "getMultiSourceChatPrompt should keep the question-relevant paragraph when trimming long source text" {
        // Six large filler paragraphs (no overlap with the question) followed by one short
        // paragraph that actually answers it. A naive prefix cut at MAX_CHARS_PER_SOURCE would
        // never reach the last paragraph — BM25-ranked selection should surface it anyway.
        val filler = "This paragraph discusses unrelated classroom logistics and general course " +
            "policies repeated for padding purposes. ".repeat(60)
        val fillerParagraphs = (1..6).joinToString("\n\n") { filler }
        val relevantParagraph = "The attendance policy allows two absences before a grade penalty applies."
        val longText = "$fillerParagraphs\n\n$relevantParagraph"

        val sources = listOf(
            SourceContextBlock("Syllabus", "SYLLABUS", null, longText)
        )
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, emptyList(), "What's the attendance policy?")

        result.shouldContain("attendance policy allows two absences")
        result.shouldContain("[content truncated]")
    }

    "getMultiSourceChatPrompt should include the student's question" {
        val sources = listOf(
            SourceContextBlock("Syllabus", "SYLLABUS", null, "Info")
        )
        val question = "What's the attendance policy?"
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, emptyList(), question)

        result.shouldContain("Student's Question")
        result.shouldContain(question)
    }

    "getMultiSourceChatPrompt should instruct to use only provided materials" {
        val result = ChatBuilder.getMultiSourceChatPrompt(emptyList(), emptyList(), "Test?")

        result.shouldContain("ONLY on the provided materials")
        result.shouldContain("Do not use outside knowledge")
    }

    "getMultiSourceChatPrompt should use the MEMORANDUM BRIEF format and XML tags" {
        val sources = listOf(
            SourceContextBlock("Syllabus", "SYLLABUS", "Policies", "Content")
        )
        val history = listOf("User" to "Hi", "Assistant" to "Hello")
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, history, "Question?")

        result.shouldContain("MEMORANDUM BRIEF: MULTI-SOURCE CHAT CONTEXT")
        result.shouldContain("## 1. TOPIC CLARIFICATION")
        result.shouldContain("## 2. STRUCTURED REFERENCE MATERIAL")
        result.shouldContain("## 3. TASK PROMPT")
        result.shouldContain("## 4. CONSTRAINTS & GUARDRAILS")

        result.shouldContain("<course_materials>")
        result.shouldContain("Syllabus")
        result.shouldContain("Policies & Rules")
        result.shouldContain("Content")
        result.shouldContain("</course_materials>")

        result.shouldContain("<conversation_history>")
        result.shouldContain("Student: Hi")
        result.shouldContain("Assistant: Hello")
        result.shouldContain("</conversation_history>")
    }

    "getMultiSourceChatPrompt should prepend the summary block ahead of verbatim history when a summary is supplied" {
        val sources = listOf(SourceContextBlock("Syllabus", "SYLLABUS", null, "Content"))
        val history = listOf("Assistant" to "Sure, group projects are due Friday.")

        val result = ChatBuilder.getMultiSourceChatPrompt(
            sources, history, "And the final?",
            summary = "Earlier: student asked about grading (40% final, 30% midterm)."
        )

        result.shouldContain("Conversation summary so far: Earlier: student asked about grading")
        result.shouldContain("Assistant: Sure, group projects are due Friday.")
    }

    "getMultiSourceChatPrompt should not truncate history to MAX_HISTORY_TURNS when a summary is present" {
        val sources = listOf(SourceContextBlock("Syllabus", "SYLLABUS", null, "Content"))
        val longHistory = (1..20).map { "User" to "turn $it" }

        val result = ChatBuilder.getMultiSourceChatPrompt(
            sources, longHistory, "Latest?", summary = "Summary of turns before these."
        )

        result.shouldContain("turn 1")
        result.shouldContain("turn 20")
    }

    "getMultiSourceChatPrompt should show a no-further-messages note when the summary covers everything" {
        val sources = listOf(SourceContextBlock("Syllabus", "SYLLABUS", null, "Content"))

        val result = ChatBuilder.getMultiSourceChatPrompt(
            sources, emptyList(), "Question?", summary = "Everything so far is summarized."
        )

        result.shouldContain("(No further messages since the summary above)")
    }

    "getMultiSourceChatPrompt should keep the naive takeLast behavior when no summary is supplied" {
        val sources = listOf(SourceContextBlock("Syllabus", "SYLLABUS", null, "Content"))
        val longHistory = (1..15).map { "User" to "turn $it" }

        val result = ChatBuilder.getMultiSourceChatPrompt(sources, longHistory, "Latest?")

        result.shouldNotContain("turn 1\n")
        result.shouldContain("turn 15")
        result.shouldNotContain("Conversation summary so far")
    }

    "getMultiSourceChatPrompt should keep a long history fully verbatim when historyAlreadyBudgeted is true, even without a summary" {
        // Regression test: truncation must be gated on historyAlreadyBudgeted, not on whether a
        // summary string happens to be present — a long-but-under-budget history (no fold has
        // triggered yet) must not be silently re-cut to MAX_HISTORY_TURNS.
        val sources = listOf(SourceContextBlock("Syllabus", "SYLLABUS", null, "Content"))
        val longHistory = (1..15).map { "User" to "turn $it" }

        val result = ChatBuilder.getMultiSourceChatPrompt(
            sources, longHistory, "Latest?", historyAlreadyBudgeted = true
        )

        result.shouldContain("turn 1\n")
        result.shouldContain("turn 15")
    }

    "getConversationSummaryPrompt should fold turns and existing summary into a compaction prompt" {
        val turns = listOf(
            "User" to "When is the final?",
            "Assistant" to "December 12th, worth 40%."
        )
        val result = ChatBuilder.getConversationSummaryPrompt("Prior context about the syllabus.", turns)

        result.shouldContain("MEMORANDUM BRIEF: CONVERSATION SUMMARY COMPACTION")
        result.shouldContain("<existing_summary>")
        result.shouldContain("Prior context about the syllabus.")
        result.shouldContain("</existing_summary>")
        result.shouldContain("<turns_to_fold>")
        result.shouldContain("Student: When is the final?")
        result.shouldContain("Assistant: December 12th, worth 40%.")
        result.shouldContain("</turns_to_fold>")
    }

    "getConversationSummaryPrompt should note when there is no existing summary yet" {
        val result = ChatBuilder.getConversationSummaryPrompt(null, listOf("User" to "Hi"))

        result.shouldContain("(No summary yet)")
    }

    "getChatCritiquePrompt should use the MEMORANDUM BRIEF format and XML tags" {
        val prompt = "Course uses Canvas. Final is 30%."
        val response = "The course uses Canvas and the final is 30%."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("MEMORANDUM BRIEF: CHAT RESPONSE QUALITY AUDIT")
        result.shouldContain("## 1. TOPIC CLARIFICATION")
        result.shouldContain("## 2. STRUCTURED REFERENCE MATERIAL")
        result.shouldContain("## 3. TASK PROMPT")
        result.shouldContain("## 4. CONSTRAINTS & GUARDRAILS")

        result.shouldContain("<original_prompt_context>")
        result.shouldContain(prompt)
        result.shouldContain("</original_prompt_context>")

        result.shouldContain("<generated_chat_response>")
        result.shouldContain(response)
        result.shouldContain("</generated_chat_response>")
    }

    "getChatCritiquePrompt should check for hallucinations" {
        val prompt = "Class meets MWF 9-10 AM"
        val response = "The professor always gives pop quizzes on Mondays."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("Hallucinations")
        result.shouldContain("fabrications")
        result.shouldContain("outside assumptions")
    }

    "getChatCritiquePrompt should allow unchanged response if fully factual" {
        val prompt = "Grading: 40% Final, 30% Midterm, 30% Quizzes"
        val response = "The final exam is worth 40% of your grade."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("fully factual")
        result.shouldContain("completely unchanged")
    }

    "getChatCritiquePrompt should instruct to cite source materials" {
        val prompt = "Source: Syllabus says classes are on campus."
        val response = "Classes are held on campus."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("source materials")
        result.shouldContain("supported by the source")
    }

    "getChatCritiquePrompt should request revision if unsupported" {
        val prompt = "No information about office hours provided."
        val response = "Office hours are Tuesdays 2-4 PM."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("do not have enough information")
        result.shouldContain("based on the provided materials")
    }

    "getChatCritiquePrompt should avoid meta-commentary in output" {
        val prompt = "Quick context"
        val response = "Brief answer"
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("Do NOT include")
        result.shouldContain("explanations")
        result.shouldContain("meta-commentary")
    }
})


