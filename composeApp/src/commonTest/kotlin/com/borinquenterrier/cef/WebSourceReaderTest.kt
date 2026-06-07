package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class WebSourceReaderTest : FunSpec({

    test("cleanHtml should remove tags and scripts but keep text") {
        val reader = WebSourceReader()
        val rawHtml = """
            <html>
                <head><script>alert('spam')</script></head>
                <body>
                    <nav>Menu</nav>
                    <h1>Course Schedule</h1>
                    <p>Exam on <b>2024-12-10</b>.</p>
                    <style>.css { color: red; }</style>
                </body>
            </html>
        """.trimIndent()

        val cleanText = reader.cleanHtml(rawHtml)

        cleanText shouldNotContain "<script>"
        cleanText shouldNotContain "<style>"
        cleanText shouldNotContain "<html>"
        cleanText shouldContain "Course Schedule"
        cleanText shouldContain "Exam on 2024-12-10"
    }

    test("should clean HTML with correct script and style removal") {
        val html = "<html><body><script>alert('Hello World!');</script><style>body { background-color: blue; }</style></body></html>"
        val expected = "" // Because both script and style are removed, and body has no direct text

        val reader = WebSourceReader()
        val result = reader.cleanHtml(html)
        result shouldBe expected
    }

    test("should clean HTML with correct tag removal") {
        val html = "<p>Hello <span>World!</span></p>"
        val expected = "Hello World!"

        val reader = WebSourceReader()
        val result = reader.cleanHtml(html)
        result shouldBe expected
    }

    test("should clean HTML with correct entity decoding") {
        val html = "&nbsp;&amp;&lt;&gt;&quot;"
        val expected = "&<>\""

        val reader = WebSourceReader()
        val result = reader.cleanHtml(html)
        result shouldBe expected
    }

    test("should return correctly formatted text after normalization and trimming of whitespace") {
        val html = "<html><body>   Hello World!   </body></html>"
        val expected = "Hello World!"

        val reader = WebSourceReader()
        val result = reader.cleanHtml(html)
        result shouldBe expected
    }
})
