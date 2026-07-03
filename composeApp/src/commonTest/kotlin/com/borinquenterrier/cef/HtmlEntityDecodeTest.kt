package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HtmlEntityDecodeTest : FunSpec({

    test("decodes the entities Google returns in event summaries") {
        decodeHtmlEntities("Assign Issue Brief #3 &amp; watch &quot;Supermax&quot;") shouldBe
            "Assign Issue Brief #3 & watch \"Supermax\""
        decodeHtmlEntities("a &lt; b &gt; c") shouldBe "a < b > c"
        decodeHtmlEntities("it&#39;s fine") shouldBe "it's fine"
        decodeHtmlEntities("it&apos;s fine") shouldBe "it's fine"
    }

    test("leaves plain titles untouched (so decoded titles match local exactly)") {
        decodeHtmlEntities("Issue Brief #2 due") shouldBe "Issue Brief #2 due"
    }

    test("&amp; is decoded last so an encoded entity name is not double-decoded") {
        // Google encodes a literal "&lt;" in a title as "&amp;lt;"; it must decode to "&lt;", not "<".
        decodeHtmlEntities("&amp;lt;") shouldBe "&lt;"
    }
})
