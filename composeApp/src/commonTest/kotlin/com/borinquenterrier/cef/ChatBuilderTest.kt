package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class ChatBuilderTest : StringSpec({

    "getMultiSourceChatPrompt should handle empty source blocks" {
        val result = ChatBuilder.getMultiSourceChatPrompt(emptyList(), emptyList(), "What's the grading scale?")

        result.shouldContain("No course materials are loaded yet")
        result.shouldNotBeBlank()
    }

    "getMultiSourceChatPrompt should include source blocks with metadata" {
        val sources = listOf(
            SourceContextBlock("CS101 Syllabus", "SYLLABUS", "Final 30%, Quizzes 20%", "Course content here")
        )
        val result = ChatBuilder.getMultiSourceChatPrompt(sources, emptyList(), "When is the final?")

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

        result.shouldContain("Prior Conversation")
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

    "getChatCritiquePrompt should validate response against original prompt" {
        val prompt = "Course uses Canvas. Final is 30% of grade."
        val response = "The course uses Blackboard and the final is optional."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("factual critique")
        result.shouldContain("quality control")
        result.shouldContain(prompt)
        result.shouldContain(response)
    }

    "getChatCritiquePrompt should check for hallucinations" {
        val prompt = "Class meets MWF 9-10 AM"
        val response = "The professor always gives pop quizzes on Mondays."
        val result = ChatBuilder.getChatCritiquePrompt(prompt, response)

        result.shouldContain("hallucinations")
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
        result.shouldContain("explicitly stated")
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

        result.shouldContain("Do not add any intros")
        result.shouldContain("explanations")
        result.shouldContain("meta-commentary")
    }
})
