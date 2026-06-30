package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class StudyPlanBuilderTest : StringSpec({

    "getSyllabusStudyPlanPrompt should use the MEMORANDUM BRIEF format and XML tags" {
        val preferences = StudyPreferences(studyStartHour = 8, studyEndHour = 22)
        val syllabusText = "Exam on Dec 15"
        val existingSchedule = "Class: MWF 9-10 AM"
        val result = StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule, preferences)

        result.shouldContain("MEMORANDUM BRIEF: STUDY PLAN AND DELIVERABLE EXTRACTION")
        result.shouldContain("## 1. TOPIC CLARIFICATION")
        result.shouldContain("## 2. STRUCTURED REFERENCE MATERIAL")
        result.shouldContain("## 3. TASK PROMPT")
        result.shouldContain("## 4. CONSTRAINTS & GUARDRAILS")

        result.shouldContain("<source_syllabus_document>")
        result.shouldContain(syllabusText)
        result.shouldContain("</source_syllabus_document>")

        result.shouldContain("<existing_schedule>")
        result.shouldContain(existingSchedule)
        result.shouldContain("</existing_schedule>")
    }

    "getSyllabusStudyPlanPrompt should include study preferences constraints" {
        val preferences = StudyPreferences(studyStartHour = 8, studyEndHour = 22)
        val syllabusText = "Exam on Dec 15"
        val result =
            StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText, preferences = preferences)

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

    "getSyllabusStudyPlanPrompt should say 'None' when no existing schedule provided" {
        val syllabusText = "Quiz on next Friday"
        val result =
            StudyPlanBuilder.getSyllabusStudyPlanPrompt(syllabusText, existingSchedule = "")

        result.shouldContain("None")
    }

    "getTaskDecompositionPrompt should use the MEMORANDUM BRIEF format" {
        val taskTitle = "Research Paper"
        val dueDate = "2024-04-15"
        val result = StudyPlanBuilder.getTaskDecompositionPrompt(taskTitle, dueDate)

        result.shouldContain("MEMORANDUM BRIEF: TASK DECOMPOSITION")
        result.shouldContain("## 1. TOPIC CLARIFICATION")
        result.shouldContain("## 2. STRUCTURED REFERENCE MATERIAL")
        result.shouldContain("## 3. TASK PROMPT")
        result.shouldContain("## 4. CONSTRAINTS & GUARDRAILS")
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

    "getDecompositionCritiquePrompt should use the MEMORANDUM BRIEF format and XML tags" {
        val taskTitle = "Final Project"
        val dueDate = "2024-05-20"
        val tasksJson =
            """[{"title":"Research","daysBeforeDue":10,"description":"Gather sources"}]"""
        val result = StudyPlanBuilder.getDecompositionCritiquePrompt(taskTitle, dueDate, tasksJson)

        result.shouldContain("MEMORANDUM BRIEF: TASK DECOMPOSITION QUALITY AUDIT")
        result.shouldContain("## 1. TOPIC CLARIFICATION")
        result.shouldContain("## 2. STRUCTURED REFERENCE MATERIAL")
        result.shouldContain("## 3. TASK PROMPT")
        result.shouldContain("## 4. CONSTRAINTS & GUARDRAILS")

        result.shouldContain("<sub_tasks_json>")
        result.shouldContain(tasksJson)
        result.shouldContain("</sub_tasks_json>")
    }

    "getDecompositionCritiquePrompt should validate sub-tasks" {
        val taskTitle = "Final Project"
        val dueDate = "2024-05-20"
        val tasksJson =
            """[{"title":"Research","daysBeforeDue":10,"description":"Gather sources"}]"""
        val result = StudyPlanBuilder.getDecompositionCritiquePrompt(taskTitle, dueDate, tasksJson)

        result.shouldContain("executive function coach")
        result.shouldContain(taskTitle)
        result.shouldContain(dueDate)
        result.shouldContain("refined JSON array")
    }

    // ── getStudyPlanCritiquePrompt ────────────────────────────────────────────

    "getStudyPlanCritiquePrompt uses MEMORANDUM BRIEF format and XML tags" {
        val result = StudyPlanBuilder.getStudyPlanCritiquePrompt(
            syllabusText = "BIOL 101 meets MWF 9-10",
            eventsJson = """[{"title":"Class","category":"CLASS","date":"2026-08-25"}]"""
        )
        result.shouldContain("MEMORANDUM BRIEF: STUDY PLAN QUALITY AUDIT")
        result.shouldContain("## 1. TOPIC CLARIFICATION")
        result.shouldContain("## 2. STRUCTURED REFERENCE MATERIAL")
        result.shouldContain("## 3. TASK PROMPT")
        result.shouldContain("## 4. CONSTRAINTS & GUARDRAILS")

        result.shouldContain("<source_syllabus_document>")
        result.shouldContain("<study_plan_json>")
        result.shouldContain("</study_plan_json>")
    }

    "getStudyPlanCritiquePrompt forbids CLASS category" {
        val result = StudyPlanBuilder.getStudyPlanCritiquePrompt(
            syllabusText = "BIOL 101 meets MWF 9-10",
            eventsJson = """[{"title":"Class","category":"CLASS","date":"2026-08-25"}]"""
        )
        result.shouldContain("DO NOT")
        result.shouldContain("CLASS")
    }

    "getStudyPlanCritiquePrompt lists only allowed categories" {
        val result = StudyPlanBuilder.getStudyPlanCritiquePrompt("syllabus", "[]")
        result.shouldContain("STUDY_BLOCK")
        result.shouldContain("REGULAR")
        result.shouldContain("DEADLINE")
        result.shouldContain("FINALS")
    }

    "getStudyPlanCritiquePrompt embeds the syllabus and events json" {
        val syllabus = "UNIQUE_SYLLABUS_MARKER"
        val json = """[{"title":"UNIQUE_EVENT"}]"""
        val result = StudyPlanBuilder.getStudyPlanCritiquePrompt(syllabus, json)
        result.shouldContain(syllabus)
        result.shouldContain(json)
    }

    "getSyllabusStudyPlanPrompt explicitly forbids CLASS events" {
        val result = StudyPlanBuilder.getSyllabusStudyPlanPrompt("Some syllabus")
        result.shouldContain("Do NOT generate CLASS events")
    }

    // ── getTaskDecompositionPrompt urgency tiers ──────────────────────────────

    "getTaskDecompositionPrompt uses EMERGENCY wording when due today" {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val result = StudyPlanBuilder.getTaskDecompositionPrompt("Essay", today.toString())
        result.shouldContain("EMERGENCY")
        result.shouldContain("2–4")
    }

    "getTaskDecompositionPrompt uses TIGHT wording for 1-2 day deadline" {
        val soon = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(1, DateTimeUnit.DAY)
        val result = StudyPlanBuilder.getTaskDecompositionPrompt("Essay", soon.toString())
        result.shouldContain("TIGHT")
        result.shouldContain("3–5")
    }

    "getTaskDecompositionPrompt uses SHORT wording for 3-7 day deadline" {
        val medium = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(5, DateTimeUnit.DAY)
        val result = StudyPlanBuilder.getTaskDecompositionPrompt("Essay", medium.toString())
        result.shouldContain("SHORT")
        result.shouldContain("4–7")
    }

    "getTaskDecompositionPrompt uses NORMAL wording for 8+ day deadline" {
        val distant = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(14, DateTimeUnit.DAY)
        val result = StudyPlanBuilder.getTaskDecompositionPrompt("Research Paper", distant.toString())
        result.shouldContain("NORMAL")
        result.shouldContain("5–9")
    }

    // ── getDecompositionCritiquePrompt step cap ───────────────────────────────

    "getDecompositionCritiquePrompt caps at 5 steps for tight deadlines" {
        val soon = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(2, DateTimeUnit.DAY)
        val result = StudyPlanBuilder.getDecompositionCritiquePrompt("Essay", soon.toString(), "[]")
        result.shouldContain("5")
    }

    "getDecompositionCritiquePrompt caps at 9 steps for normal timeline" {
        val far = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(30, DateTimeUnit.DAY)
        val result = StudyPlanBuilder.getDecompositionCritiquePrompt("Research", far.toString(), "[]")
        result.shouldContain("9")
    }

    "getDecompositionCritiquePrompt handles unparseable due date gracefully" {
        val result = StudyPlanBuilder.getDecompositionCritiquePrompt("Task", "not-a-date", "[]")
        result.shouldContain("30") // fallback daysAvailable = 30
    }
})
