package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class StudyPlanBuilderTest : StringSpec({

    "getSyllabusStudyPlanPrompt should include study preferences constraints" {
        val preferences = StudyPreferences(studyStartHour = 8, studyEndHour = 22)
        val syllabusText = "Exam on Dec 15"
        val result = StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText, preferences = preferences)

        result.shouldContain("STUDY_BLOCK")
        result.shouldContain("proactively suggest")
        result.shouldContain("gradeWeight")
        result.shouldNotBeBlank()
    }

    "getSyllabusStudyPlanPrompt should use current year" {
        val syllabusText = "Midterm on February 14"
        val result = StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText)

        result.shouldNotBeBlank()
    }

    "getSyllabusStudyPlanPrompt should include existing schedule when provided" {
        val syllabusText = "Final exam on May 10"
        val existingSchedule = "Class: MWF 9-10 AM"
        val result = StudyPlanBuilder.getSyllabusStudyPlanPrompt(
            syllabusText,
            existingSchedule = existingSchedule
        )

        result.shouldContain(existingSchedule)
    }

    "getSyllabusStudyPlanPrompt should say 'None' when no existing schedule provided" {
        val syllabusText = "Quiz on next Friday"
        val result = StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule = "")

        result.shouldContain("None")
    }

    "getTaskDecompositionPrompt should format task title and due date" {
        val taskTitle = "Research Paper"
        val dueDate = "2024-04-15"
        val result = StudyPlanBuilder.getTaskDecompositionPrompt(taskTitle, dueDate)

        result.shouldContain(taskTitle)
        result.shouldContain(dueDate)
        result.shouldContain("Executive Function Coach")
        result.shouldContain("JSON array")
    }

    "getTaskDecompositionPrompt should include optional context" {
        val taskTitle = "Essay"
        val dueDate = "2024-03-01"
        val context = "Should be 5000 words minimum"
        val result = StudyPlanBuilder.getTaskDecompositionPrompt(taskTitle, dueDate, context)

        result.shouldContain(context)
    }

    "getDecompositionCritiquePrompt should validate sub-tasks" {
        val taskTitle = "Final Project"
        val dueDate = "2024-05-20"
        val tasksJson = """[{"title":"Research","daysBeforeDue":10,"description":"Gather sources"}]"""
        val result = StudyPlanBuilder.getDecompositionCritiquePrompt(taskTitle, dueDate, tasksJson)

        result.shouldContain("executive function coach")
        result.shouldContain(taskTitle)
        result.shouldContain(dueDate)
        result.shouldContain("refined JSON array")
    }
})
