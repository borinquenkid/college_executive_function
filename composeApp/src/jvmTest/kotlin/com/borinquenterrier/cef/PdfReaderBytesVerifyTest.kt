package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Proves the new bytes path extracts real text from a real syllabus PDF (not the %PDF garbage the
 * Drive path used to store). Skips if the sample isn't present (e.g. a checkout without contributions/).
 */
class PdfReaderBytesVerifyTest : FunSpec({
    val pdf = File("contributions/mo/st_louis_community_college/2025-2026/summer/syllabi-202620-eng-101-601-20366.pdf")

    test("PdfReader.readSource(bytes) extracts readable text from the real syllabus PDF") {
        if (!pdf.exists()) return@test // sample not checked out

        val fragments = runBlocking { PdfReader().readSource(pdf.readBytes()) }
        val text = fragments.joinToString("\n") { it.text }

        text.length shouldBeGreaterThan 10_000
        text shouldContain "ENG 101"
        text shouldContain "Juneteenth"
        // must NOT be raw PDF markup
        text.contains("%PDF-1.4") shouldBe false
    }
})
