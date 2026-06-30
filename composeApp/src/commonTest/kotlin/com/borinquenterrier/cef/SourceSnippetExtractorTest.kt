package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Finds the slice of source text most relevant to a deliverable, so the date-picker dialog can
 * show the user *where* the item came from. Returns null when nothing in the source overlaps the
 * title — a signal that the deliverable is likely fabricated rather than just mis-dated.
 */
class SourceSnippetExtractorTest : FunSpec({

    val source = """
        Week 1: Course introduction and syllabus review.
        Essay on witness protection due July 15.
        Final exam covering all units on August 10.
    """.trimIndent()

    test("returns the sentence sharing the most words with the title") {
        val snippet = SourceSnippetExtractor.snippet(source, "Submit Essay")
        snippet shouldContain "Essay on witness protection"
    }

    test("is case-insensitive") {
        SourceSnippetExtractor.snippet(source, "FINAL EXAM")!!.shouldContain("Final exam")
    }

    test("returns null when no source text overlaps the title (likely fabricated)") {
        SourceSnippetExtractor.snippet(source, "Quantum chromodynamics midterm") shouldBe null
    }

    test("ignores trivial short words when scoring") {
        // "the", "on", "of" must not create spurious matches; only substantive words count.
        SourceSnippetExtractor.snippet(source, "on the of a") shouldBe null
    }
})
