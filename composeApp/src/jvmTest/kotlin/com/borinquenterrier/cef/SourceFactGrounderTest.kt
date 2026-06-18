package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SourceFactGrounderTest : FunSpec({

    // --- extractClaims ---

    test("extracts spelled-out month-day date from response") {
        val claims = SourceFactGrounder.extractClaims("Your midterm is on October 14.")
        claims shouldContain "October 14"
    }

    test("extracts abbreviated month-day date from response") {
        val claims = SourceFactGrounder.extractClaims("Essay due Nov 3.")
        claims shouldContain "Nov 3"
    }

    test("strips ordinal suffix when extracting dates") {
        val claims = SourceFactGrounder.extractClaims("Final exam on December 10th.")
        claims shouldContain "December 10"
        claims shouldNotContain "December 10th"
    }

    test("extracts grade weight percentage") {
        val claims = SourceFactGrounder.extractClaims("This assignment is worth 40% of your grade.")
        claims shouldContain "40%"
    }

    test("extracts multiple distinct claims") {
        val text = "Midterm on October 14 worth 25%. Final on December 10 worth 40%."
        val claims = SourceFactGrounder.extractClaims(text)
        claims shouldContain "October 14"
        claims shouldContain "December 10"
        claims shouldContain "25%"
        claims shouldContain "40%"
    }

    test("does not extract year-only references as dates") {
        val claims = SourceFactGrounder.extractClaims("This course runs through May 2026.")
        claims shouldNotContain "May 2026"
        claims shouldNotContain "May 20"
    }

    test("does not extract bare month names without day numbers") {
        val claims = SourceFactGrounder.extractClaims("May I remind you that fall semester is busy.")
        claims.shouldBeEmpty()
    }

    test("deduplicates repeated claims") {
        val claims = SourceFactGrounder.extractClaims("October 14 exam. Study for October 14.")
        claims.count { it == "October 14" } shouldBe 1
    }

    // --- findUngrounded ---

    test("returns empty list when all claims appear in source") {
        val source = "Midterm: October 14. Worth 25% of final grade."
        val claims = listOf("October 14", "25%")
        SourceFactGrounder.findUngrounded(claims, source).shouldBeEmpty()
    }

    test("flags date absent from source as ungrounded") {
        val source = "Midterm: October 14."
        val claims = listOf("November 28")
        SourceFactGrounder.findUngrounded(claims, source) shouldContain "November 28"
    }

    test("flags percentage absent from source as ungrounded") {
        val source = "Final exam worth 50%."
        val claims = listOf("40%")
        SourceFactGrounder.findUngrounded(claims, source) shouldContain "40%"
    }

    test("matching is case-insensitive") {
        val source = "midterm: OCTOBER 14."
        val claims = listOf("October 14")
        SourceFactGrounder.findUngrounded(claims, source).shouldBeEmpty()
    }

    // --- groundFreeText ---

    test("returns response unchanged when all claims are grounded") {
        val source = "Midterm on October 14, worth 25%."
        val response = "Your midterm is October 14 and worth 25%."
        val result = SourceFactGrounder.groundFreeText(response, source, null)
        result shouldBe response
    }

    test("appends warning when response contains ungrounded date") {
        val source = "Midterm: October 14."
        val response = "Your essay is due November 28."
        val result = SourceFactGrounder.groundFreeText(response, source, null)
        result shouldContain "November 28"
        result shouldContain "could not be verified"
    }

    test("appends warning when response contains ungrounded percentage") {
        val source = "Final exam worth 50%."
        val response = "This assignment counts for 15% of your grade."
        val result = SourceFactGrounder.groundFreeText(response, source, null)
        result shouldContain "15%"
        result shouldContain "could not be verified"
    }

    test("returns response unchanged when source is blank") {
        val response = "Your midterm is October 14."
        val result = SourceFactGrounder.groundFreeText(response, "", null)
        result shouldBe response
    }

    test("returns response unchanged when response has no extractable claims") {
        val source = "Midterm: October 14."
        val response = "Great question! Let me help you understand the course structure."
        val result = SourceFactGrounder.groundFreeText(response, source, null)
        result shouldBe response
    }

    test("warning lists all ungrounded claims") {
        val source = "Quiz on September 5."
        val response = "Your midterm is October 14 worth 30%. Essay due November 28."
        val result = SourceFactGrounder.groundFreeText(response, source, null)
        result shouldContain "October 14"
        result shouldContain "30%"
        result shouldContain "November 28"
    }
})
