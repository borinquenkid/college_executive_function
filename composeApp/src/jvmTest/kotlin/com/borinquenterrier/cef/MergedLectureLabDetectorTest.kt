package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Titles are taken from real nightly eval output (UT BIO 325L spring). */
class MergedLectureLabDetectorTest : FunSpec({

    test("a lecture row merged with a lab row is flagged") {
        isMergedLectureLab("Monday 2/16 Lecture: Epistasis and Yeast Analysis Wrap-up & F2 Lab 6: Collection of F2 Data") shouldBe true
        isMergedLectureLab("Lecture: Intro to GMOs / Lab 8: GMO 1: DNA Extraction and PCR") shouldBe true
    }

    test("a lab row whose reading is 'Lecture Slides' is not a merge") {
        isMergedLectureLab("Lab 14: Western Blot and Phylogenetic tree of fish sequences (Phylogenetics: Lecture Slides)") shouldBe false
        isMergedLectureLab("Western Blot: Exp. 9 pp 158, 161-165 & Phylogenetics: Lecture Slides / Lab 14: Western Blot and Phylogenetic tree of fish sequences & Exam Q&A period during lab") shouldBe false
    }

    test("a lecture or a lab on its own is not a merge") {
        isMergedLectureLab("Lecture: Microscopy basics") shouldBe false
        isMergedLectureLab("Lab 1: Microscopy") shouldBe false
    }
})
