package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The URL source box accepts multiple links at once (like the multi-select file picker). This
 * splits a pasted blob into individual URLs.
 */
class UrlListParserTest : FunSpec({

    test("returns a single url unchanged") {
        UrlListParser.parse("https://a.com/syllabus.pdf") shouldBe listOf("https://a.com/syllabus.pdf")
    }

    test("splits on newlines, commas, and surrounding whitespace") {
        UrlListParser.parse("  https://a.com ,\n https://b.com\n\thttps://c.com ") shouldBe
            listOf("https://a.com", "https://b.com", "https://c.com")
    }

    test("drops blank entries and de-duplicates") {
        UrlListParser.parse("https://a.com,,https://a.com\n\n https://b.com") shouldBe
            listOf("https://a.com", "https://b.com")
    }

    test("empty or whitespace-only input yields an empty list") {
        UrlListParser.parse("   \n , \t ") shouldBe emptyList()
        UrlListParser.parse("") shouldBe emptyList()
    }
})
