package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class LogFileCapJvmTest : FunSpec({
    test("capLogFile uses MAX_LOG_FILE_BYTES as the default cap when none is given") {
        val file = File.createTempFile("cef_debug_log_test", ".txt")
        file.deleteOnExit()
        file.writeText("short")

        capLogFile(file)

        file.readText() shouldBe "short"
    }

    test("capLogFile trims a file that has grown past the cap") {
        val file = File.createTempFile("cef_debug_log_test", ".txt")
        file.deleteOnExit()
        file.writeText("a".repeat(1000))

        capLogFile(file, maxBytes = 100)

        file.length() shouldBe 100L
        file.readText() shouldBe "a".repeat(100)
    }

    test("capLogFile leaves a file under the cap untouched") {
        val file = File.createTempFile("cef_debug_log_test", ".txt")
        file.deleteOnExit()
        file.writeText("short")

        capLogFile(file, maxBytes = 100)

        file.readText() shouldBe "short"
    }

    test("repeated appends past the cap never let the file grow unbounded") {
        val file = File.createTempFile("cef_debug_log_test", ".txt")
        file.deleteOnExit()

        repeat(200) {
            file.appendText("line-$it\n")
            capLogFile(file, maxBytes = 500)
        }

        (file.length() <= 500) shouldBe true
    }
})
