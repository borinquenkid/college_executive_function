package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class AcademicSynonymsTest : FunSpec({

    test("expands a student term to its formal-syllabus siblings") {
        val expanded = AcademicSynonyms.expandQuery("when is my essay due?")

        expanded shouldContain "essay"
        expanded shouldContain "paper"
        expanded shouldContain "brief"
        expanded shouldContain "deadline"
    }

    test("does not duplicate terms already in the query") {
        val expanded = AcademicSynonyms.expandQuery("essay paper")

        Regex("\\bpaper\\b").findAll(expanded).count() shouldBe 1
    }

    test("queries with no academic vocabulary pass through unchanged") {
        AcademicSynonyms.expandQuery("what is the meaning of life?") shouldBe
            "what is the meaning of life?"
    }

    test("original query text is preserved verbatim as the prefix") {
        val query = "When is the Primary Source Image Project due?"
        AcademicSynonyms.expandQuery(query).startsWith(query) shouldBe true
    }

    test("expansion crosses singular and plural forms without a stemmer") {
        val expanded = AcademicSynonyms.expandQuery("do I need to buy books")

        expanded shouldContain "textbooks"
        expanded shouldContain "textbook"
    }

    test("expansion never rewrites into document text — only appends") {
        val expanded = AcademicSynonyms.expandQuery("is class attendance required?")

        expanded shouldContain "is class attendance required?"
        expanded shouldContain "compulsory"
        expanded shouldNotContain "  " // clean single-space joins
    }
})
