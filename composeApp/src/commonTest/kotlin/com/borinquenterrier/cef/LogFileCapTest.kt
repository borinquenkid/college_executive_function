package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LogFileCapTest : FunSpec({
    test("uses MAX_LOG_FILE_BYTES as the default cap when none is given") {
        capLogContent("short") shouldBe "short"
    }

    test("content under the cap is returned unchanged") {
        capLogContent("short", maxBytes = 100) shouldBe "short"
    }

    test("content exactly at the cap is returned unchanged") {
        val content = "a".repeat(100)
        capLogContent(content, maxBytes = 100) shouldBe content
    }

    test("content past the cap is trimmed to the most recent maxBytes characters") {
        val content = "0123456789".repeat(20) // 200 chars
        capLogContent(content, maxBytes = 100) shouldBe content.takeLast(100)
    }

    test("repeated trimming never grows past the cap") {
        var content = ""
        repeat(50) {
            content = capLogContent(content + "line-$it\n", maxBytes = 100)
            (content.length <= 100) shouldBe true
        }
    }
})
