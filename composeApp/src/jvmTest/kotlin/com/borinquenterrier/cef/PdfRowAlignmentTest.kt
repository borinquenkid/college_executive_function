package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Table rows must survive PDF text extraction. In content-stream order PDFBox emitted UT BIO 325L's
 * schedule column by column — every date cell ("Monday 1/12", "1/13, 14, 15"), then every topic
 * cell — so nothing in the text tied "Lab 1" to 1/13 rather than 1/12. The model guessed, and
 * guessed differently run to run (merged "Lecture … / Lab 1 …" @ 1/12 on one run, correct on the
 * next; 2026-09-29).
 */
class PdfRowAlignmentTest : FunSpec({

    val root = listOf(File("../contributions"), File("contributions")).first { it.isDirectory }
    suspend fun lines(entry: ContributionIndex): List<String> =
        PdfReader().readSource(File(root, entry.relativePath).absolutePath)
            .flatMap { it.text.lines() }.map { it.replace(Regex("\\s+"), " ").trim() }

    test("UT BIO 325L spring: each schedule row's date shares a line with its own topic") {
        val text = lines(ContributionIndex.UT_BIO325L_GENETICS_LAB_SPRING)
        text.any { it.contains("Monday 1/12") && it.contains("Lecture: Microscopy basics") } shouldBe true
        text.any { it.contains("1/13, 14, 15") && it.contains("Lab 1:") } shouldBe true
        text.any { it.contains("Monday 1/19") && it.contains("MLK Day") } shouldBe true
    }

    test("COMP SCI 2300: week row keeps its dates with its topic") {
        val text = lines(ContributionIndex.MST_CS2300_SYLLABUS)
        text.any { it.contains("9/29, 10/1") && it.contains("ER-to-Relational Mapping") } shouldBe true
    }
})
