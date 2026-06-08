package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AiPromptsTest : FunSpec({

    test("formatHour through getSyllabusStudyPlanPrompt for various edge cases") {
        // hour = 0 (midnight)
        val prefs0 = StudyPreferences(studyStartHour = 0)
        val prompt0 = AiPrompts.getSyllabusStudyPlanPrompt("Syllabus", "Schedule", prefs0)
        prompt0 shouldContain "00:00 (12 AM)"

        // hour = 12 (noon)
        val prefs12 = StudyPreferences(studyStartHour = 12)
        val prompt12 = AiPrompts.getSyllabusStudyPlanPrompt("Syllabus", "Schedule", prefs12)
        prompt12 shouldContain "12:00 (12 PM)"

        // hour > 12 (PM hours, e.g. 15 -> 3 PM)
        val prefs15 = StudyPreferences(studyStartHour = 15)
        val prompt15 = AiPrompts.getSyllabusStudyPlanPrompt("Syllabus", "Schedule", prefs15)
        prompt15 shouldContain "15:00 (3 PM)"

        // hour < 12 but > 0 (AM hours, e.g. 9 -> 9 AM)
        val prefs9 = StudyPreferences(studyStartHour = 9)
        val prompt9 = AiPrompts.getSyllabusStudyPlanPrompt("Syllabus", "Schedule", prefs9)
        prompt9 shouldContain "09:00 (9 AM)"
    }

    test("verify other prompts generated correctly") {
        val text = "dummy content"
        
        AiPrompts.getSourceEventExtractionPrompt(text) shouldContain "dummy content"
        
        AiPrompts.getTaskDecompositionPrompt("Title", "2026-06-08", text) shouldContain "dummy content"
        AiPrompts.getTaskDecompositionPrompt("Title", "2026-06-08", text) shouldContain "Title"
        
        AiPrompts.getDocumentIntelligencePrompt(text) shouldContain "dummy content"
        
        AiPrompts.getSourceCategorizationPrompt(text) shouldContain "dummy content"
        
        AiPrompts.getEventCritiquePrompt("Source", "Events") shouldContain "Source"
        AiPrompts.getEventCritiquePrompt("Source", "Events") shouldContain "Events"
        
        AiPrompts.getChatCritiquePrompt("Prompt", "Response") shouldContain "Prompt"
        AiPrompts.getChatCritiquePrompt("Prompt", "Response") shouldContain "Response"
        
        AiPrompts.getDecompositionCritiquePrompt("Title", "Date", "Tasks") shouldContain "Title"
        AiPrompts.getDecompositionCritiquePrompt("Title", "Date", "Tasks") shouldContain "Tasks"
        
        AiPrompts.getSyllabusAuditPrompt(text) shouldContain "dummy content"
    }
})
