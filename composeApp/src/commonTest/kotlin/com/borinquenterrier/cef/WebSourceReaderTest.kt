package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldContain

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
        // We might want to filter out 'Menu' or nav, but basic cleaning is the priority
    }
})
