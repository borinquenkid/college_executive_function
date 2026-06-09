package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class CategorizationBuilderTest : StringSpec({

    "getSourceCategorizationPrompt should identify Syllabus category" {
        val syllabusText = "MWF 9:00-10:00 AM. Assignment due Friday."
        val result = CategorizationBuilder.getSourceCategorizationPrompt(syllabusText)

        result.shouldContain("Syllabus")
        result.shouldContain("Calendar")
        result.shouldContain("category")
        result.shouldContain("isValid")
        result.shouldNotBeBlank()
    }

    "getSourceCategorizationPrompt should validate document elements" {
        val text = "Some random document without schedule or deadlines"
        val result = CategorizationBuilder.getSourceCategorizationPrompt(text)

        result.shouldContain("validation")
        result.shouldContain("isValid")
    }

    "getSourceCategorizationPrompt should truncate large texts to 8000 chars" {
        val longText = "x".repeat(10000) + "Important: MWF 10-11 AM"
        val result = CategorizationBuilder.getSourceCategorizationPrompt(longText)

        result.shouldContain("truncated")
    }

    "getDocumentIntelligencePrompt should extract grading scale" {
        val syllabusText = "Grading: Final 30%, Midterm 20%, Quizzes 10%"
        val result = CategorizationBuilder.getDocumentIntelligencePrompt(syllabusText)

        result.shouldContain("grading_scale")
        result.shouldContain("Rules of the Game")
        result.shouldNotBeBlank()
    }

    "getDocumentIntelligencePrompt should extract all policy fields" {
        val syllabusText = "Late policy: 10% per day. Professor contact: john@university.edu"
        val result = CategorizationBuilder.getDocumentIntelligencePrompt(syllabusText)

        result.shouldContain("grading_scale")
        result.shouldContain("late_policy")
        result.shouldContain("attendance_policy")
        result.shouldContain("professor_contact")
        result.shouldContain("academic_integrity")
        result.shouldContain("required_materials")
    }

    "getSyllabusAuditPrompt should detect external LMS dependencies" {
        val syllabusText = "All assignments are posted on Blackboard. Quizzes are on Canvas."
        val result = CategorizationBuilder.getSyllabusAuditPrompt(syllabusText)

        result.shouldContain("Blackboard")
        result.shouldContain("Canvas")
        result.shouldContain("EXTERNAL_LMS")
        result.shouldContain("hasAmbiguities")
        result.shouldNotBeBlank()
    }

    "getSyllabusAuditPrompt should detect tentative schedules" {
        val syllabusText = "Schedule is tentative and subject to change. Dates may be adjusted."
        val result = CategorizationBuilder.getSyllabusAuditPrompt(syllabusText)

        result.shouldContain("TENTATIVE")
        result.shouldContain("findings")
    }

    "getSyllabusAuditPrompt should detect grading policy impacts" {
        val syllabusText = "Lowest quiz grade is dropped. Final exam is optional if midterm > 80."
        val result = CategorizationBuilder.getSyllabusAuditPrompt(syllabusText)

        result.shouldContain("GRADING_POLICY")
        result.shouldContain("severity")
    }

    "getSyllabusAuditPrompt should return JSON format" {
        val syllabusText = "Course meets MWF. No ambiguities."
        val result = CategorizationBuilder.getSyllabusAuditPrompt(syllabusText)

        result.shouldContain("{")
        result.shouldContain("}")
        result.shouldContain("hasAmbiguities")
        result.shouldContain("findings")
    }
})
