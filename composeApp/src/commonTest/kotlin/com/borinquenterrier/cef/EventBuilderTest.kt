package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class EventBuilderTest : StringSpec({

    "getSourceEventExtractionPrompt should include fragment processing instructions" {
        val fragmentJson = """{"text":"Exam on May 15","type":"TEXT"}"""
        val result = EventBuilder.getSourceEventExtractionPrompt(fragmentJson)

        result.shouldContain("Source Fragment Interpretation Instructions")
        result.shouldContain("type")
        result.shouldContain("CALENDAR")
        result.shouldContain("TEXT")
    }

    "getSourceEventExtractionPrompt should specify output JSON schema" {
        val fragmentJson = """{"text":"Quiz Friday 2 PM","type":"TEXT"}"""
        val result = EventBuilder.getSourceEventExtractionPrompt(fragmentJson)

        result.shouldContain("title")
        result.shouldContain("type")
        result.shouldContain("category")
        result.shouldContain("date")
        result.shouldContain("startTime")
        result.shouldContain("gradeWeight")
        result.shouldContain("warning")
    }

    "getSourceEventExtractionPrompt should include current year context" {
        val fragmentJson = """{"text":"Midterm in March","type":"TEXT"}"""
        val result = EventBuilder.getSourceEventExtractionPrompt(fragmentJson)

        result.shouldContain("Current Year")
        result.shouldNotBeBlank()
    }

    "getSourceEventExtractionPrompt should emphasize explicit extraction only" {
        val fragmentJson = """{"text":"Project due next week","type":"TEXT"}"""
        val result = EventBuilder.getSourceEventExtractionPrompt(fragmentJson)

        result.shouldContain("Do NOT invent")
        result.shouldContain("Only extract what is stated")
    }

    "getSourceEventExtractionPrompt should include the fragment JSON" {
        val fragmentJson = """{"text":"Final exam","type":"TEXT","sectionTitle":"Assessment"}"""
        val result = EventBuilder.getSourceEventExtractionPrompt(fragmentJson)

        result.shouldContain(fragmentJson)
    }

    "getEventCritiquePrompt should compare events against source text" {
        val sourceText = "Midterm exam on October 15, 10:00 AM"
        val eventsJson =
            """[{"title":"Midterm","date":"2024-10-15","startTime":"10:00","category":"FINALS"}]"""
        val result = EventBuilder.getEventCritiquePrompt(sourceText, eventsJson)

        result.shouldContain("Source Document")
        result.shouldContain(sourceText)
        result.shouldContain("Extracted Events")
    }

    "getEventCritiquePrompt should check for hallucinations" {
        val sourceText = "Assignment due Friday"
        val eventsJson = """[{"title":"Assignment","date":"2024-03-01"}]"""
        val result = EventBuilder.getEventCritiquePrompt(sourceText, eventsJson)

        result.shouldContain("Hallucinated")
        result.shouldContain("invented")
    }

    "getEventCritiquePrompt should validate dates and times" {
        val sourceText = "Quiz next Tuesday 2 PM"
        val eventsJson = """[{"title":"Quiz","date":"2024-03-05","startTime":"14:00"}]"""
        val result = EventBuilder.getEventCritiquePrompt(sourceText, eventsJson)

        result.shouldContain("Incorrect dates")
        result.shouldContain("times")
    }

    "getEventCritiquePrompt should return refined JSON if changes needed" {
        val sourceText = "Homework due March 10"
        val eventsJson = """[{"title":"Homework","date":"2024-03-10","category":"DEADLINE"}]"""
        val result = EventBuilder.getEventCritiquePrompt(sourceText, eventsJson)

        result.shouldContain("corrected JSON array")
        result.shouldContain("EXACT same schema")
    }

    "getEventCritiquePrompt should instruct to not include filler" {
        val sourceText = "Test on April 20"
        val eventsJson = """[{"title":"Test","date":"2024-04-20"}]"""
        val result = EventBuilder.getEventCritiquePrompt(sourceText, eventsJson)

        result.shouldContain("Do not include any explanation")
        result.shouldContain("markdown formatting")
    }
})
