package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking

class WebSourceReaderTest : FunSpec({

    fun readerWith(engine: MockEngine) = WebSourceReader(HttpClient(engine))

    test("readBytesFromUrl throws on a non-2xx response instead of returning the error page as content") {
        val engine = MockEngine { respond("<html>404 Not Found</html>", HttpStatusCode.NotFound, headersOf("Content-Type", "text/html")) }
        shouldThrow<Exception> {
            runBlocking { readerWith(engine).readBytesFromUrl("https://example.com/missing.pdf") }
        }
    }

    test("readBytesFromUrl returns the body bytes on a 2xx response") {
        val engine = MockEngine { respond(byteArrayOf(1, 2, 3, 4), HttpStatusCode.OK, headersOf("Content-Type", "application/octet-stream")) }
        val bytes = runBlocking { readerWith(engine).readBytesFromUrl("https://example.com/file.pdf") }
        bytes shouldBe byteArrayOf(1, 2, 3, 4)
    }

    test("readTextFromUrl returns an error message (not the error page body) on a non-2xx response") {
        val errorPageMarker = "<html>the actual 500 page body, should never leak into the result</html>"
        val engine = MockEngine { respond(errorPageMarker, HttpStatusCode.InternalServerError, headersOf("Content-Type", "text/html")) }
        val result = runBlocking { readerWith(engine).readTextFromUrl("https://example.com/page") }
        result shouldContain "Error loading content from URL"
        result shouldNotContain errorPageMarker
    }

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
        val html =
            "<html><body><script>alert('Hello World!');</script><style>body { background-color: blue; }</style></body></html>"
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
