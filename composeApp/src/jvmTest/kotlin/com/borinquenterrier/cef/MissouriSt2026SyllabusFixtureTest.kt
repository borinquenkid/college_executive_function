package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Deterministic (no AI, no network) checks on the Missouri S&T Fall 2026 contributions.
 *
 * Fixtures are scrubbed stand-ins (see [ContributionIndex]); they preserve the facts and quirks under test.
 *
 * Purpose: settle "does this syllabus even contain what the extractor needs?" *before* blaming the
 * model. Each test reads the real file through the production readers and asserts what the text
 * actually contains — a stated meeting pattern, dated anchors, or (for two documents) the absence
 * of either. The live-AI depth assertions for the same files are in [ContributorPdfIntegrationTest]
 * (entries `MST_*` in [ContributionIndex]).
 */
class MissouriSt2026SyllabusFixtureTest : FunSpec({

    val root = listOf(File("../contributions"), File("contributions"))
        .firstOrNull { it.isDirectory }
        ?: error("contributions/ not found from ${File(".").canonicalPath}")
    val dir = File(root, "mo/missouri_university_of_science_and_technology/2026-2027/fall")

    suspend fun textOf(entry: ContributionIndex): String {
        val file = File(root, entry.relativePath)
        val fragments = when (file.extension.lowercase()) {
            "pdf" -> PdfReader().readSource(file.absolutePath)
            else -> error("unsupported ${file.name}")
        }
        return fragments.joinToString(" ") { it.text }.replace(Regex("[\\s\\u00A0]+"), " ")
    }

    // m/d or "Month d" style dates a schedule table would be built from.
    val numericDate = Regex("""\b(8|9|10|11|12)/\d{1,2}\b""")
    val monthDate = Regex("""\b(Aug|Sept?|Oct|Nov|Dec)[a-z]*\.?\s+\d{1,2}\b""")
    fun datedAnchors(text: String) = numericDate.findAll(text).count() + monthDate.findAll(text).count()

    test("COMP SCI 2300 states Tue/Thu meeting time and a dated week-by-week schedule") {
        val text = textOf(ContributionIndex.MST_CS2300_SYLLABUS)
        text shouldContain "Tue & Thu 3:30 pm to 4:45 pm"
        text shouldContain "Course Schedule"
        datedAnchors(text) shouldBeGreaterThanOrEqual 25
    }

    test("MATH 4209 states MWF 9:00 but its Course Schedule section is an unfilled placeholder") {
        val text = textOf(ContributionIndex.MST_MATH4209_SYLLABUS)
        text shouldContain "Meeting Days: MoWeFr"
        text shouldContain "09:00AM - 09:50AM"
        // The real reason this file yields little: the export ships before the schedule is written.
        text shouldContain "This will be updated soon"
        text shouldContain "Grading policies will be posted soon"
        // Only the three Canvas due dates carry a concrete date. (The "9/28/26, 22:02" browser
        // print-date footer on every page is excluded by the trailing-comma lookahead.)
        Regex("""\b\d{1,2}/\d{1,2}/26\b(?!,)""").findAll(text).map { it.value }.toSet() shouldBe
            setOf("9/2/26", "9/26/26", "9/29/26")
    }

    test("MATH 4209 contradicts itself on meeting time: office hours 8:00 vs class 9:00 in the same block") {
        val text = textOf(ContributionIndex.MST_MATH4209_SYLLABUS)
        text shouldContain "8:00am-8:45am, MWF" // office hours, must not be read as the class time
        text shouldContain "We meet on MWF in CS203 at 9:00am"
    }

    test("STAT 5643 states MWF 12:00 meeting time, 3 dated exams and a week-range lecture table") {
        val text = textOf(ContributionIndex.MST_STAT5643_SYLLABUS)
        text shouldContain "Monday, Wednesday, and Friday, 12:00 PM"
        text shouldContain "September 28, 2026"
        text shouldContain "October 30, 2026"
        text shouldContain "December 18, 2026"
        // The lecture table is week *ranges* ("Sept. 28–Oct. 2"), not per-session dates.
        text shouldContain "Sept. 28–Oct. 2"
        text shouldNotContain "9/29"
    }

    test("SPMS 1185 lists two sections' times and a per-session dated schedule") {
        val text = textOf(ContributionIndex.MST_SPMS1185_SYLLABUS)
        text shouldContain "TR 2:00"
        text shouldContain "TR 3:30-4:45"
        text shouldContain "Tue 9/29 - Informative Speeches, Day 1"
        datedAnchors(text) shouldBeGreaterThanOrEqual 30
    }

    test("SPMS 1185 body never states 2026 — only a textbook year — which must not become the term year") {
        val text = textOf(ContributionIndex.MST_SPMS1185_SYLLABUS)
        text shouldContain "(2019)" // the textbook edition year is present in the text...
        // ...but it is a citation, so it no longer counts as a source year. Before the fix this was
        // {2019}, which dropped every real 2026 event (0 events extracted, live 2026-09-29).
        GeminiAIService.extractSourceYears(text) shouldBe emptySet()
    }

    test("STAT 5353 states MWF 1:00 but has only four prose-embedded dates and no schedule table") {
        val text = textOf(ContributionIndex.MST_STAT5353_SYLLABUS)
        text shouldContain "1:00-1:50pm MWF"
        text shouldContain "Friday, October 2nd and Friday, November 20th"
        text shouldContain "week of 12/7"
        text shouldContain "Wednesday, December 16"
        text shouldNotContain "Course Schedule"
        text shouldNotContain "Week 1"
    }

    test("CS 2300 project description is an assignment spec, not a syllabus: no meeting pattern") {
        val text = textOf(ContributionIndex.MST_CS2300_PROJECT_DESCRIPTION)
        Regex("""(?i)meeting days|MWF|Tue & Thu""").containsMatchIn(text) shouldBe false
    }

    test("Fall 2026 dates-and-deadlines calendar is image-only: text layer is empty, needs vision OCR") {
        val file = File(dir, "FS2026_dates_and_deadlines_calendar.pdf")
        val fragments = PdfReader().readSource(file.absolutePath)
        PdfTextQuality.isImageOnly(fragments).shouldBeTrue()
    }
})
