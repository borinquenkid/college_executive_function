package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class StudyBlockShiftResolverTest : StringSpec({

    val date = LocalDate(2026, 9, 15)

    fun studyBlock(id: String, title: String = "Study Math") = TimeEvent(
        id = id,
        title = title,
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.STUDY_BLOCK,
        date = date,
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0)
    )

    fun classEvent(title: String = "Math 101") = TimeEvent(
        id = "class-1",
        title = title,
        source = EventSource.CLASS,
        category = AcademicCategory.CLASS,
        date = date,
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0)
    )

    "resolveShifts emits one proposal per unique title+date even when duplicates exist in localEvents" {
        val resolver = StudyBlockShiftResolver()
        // Two study blocks with same title+date but different IDs (duplicate DB rows)
        val local = listOf(studyBlock("id-1"), studyBlock("id-2"))
        val proposedBase = listOf(classEvent())

        val proposals = resolver.resolveShifts(local, proposedBase)

        // Must not produce two proposals for the same logical slot
        proposals shouldHaveSize 1
        proposals[0].originalEvent.title shouldBe "Study Math"
    }

    "resolveShifts emits no proposal when study block has no collision" {
        val resolver = StudyBlockShiftResolver()
        val block = TimeEvent(
            id = "study-1",
            title = "Study Chemistry",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0)
        )
        val proposedBase = listOf(classEvent()) // class at 9–10, no collision

        val proposals = resolver.resolveShifts(listOf(block), proposedBase)

        proposals shouldHaveSize 0
    }

    "resolveShifts emits distinct proposals for different title+date combinations" {
        val resolver = StudyBlockShiftResolver()
        val block1 = studyBlock("id-1", "Study Math")
        val block2 = TimeEvent(
            id = "id-2",
            title = "Study Physics",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK,
            date = date,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0)
        )
        val proposedBase = listOf(classEvent())

        val proposals = resolver.resolveShifts(listOf(block1, block2), proposedBase)

        proposals shouldHaveSize 2
    }
})
