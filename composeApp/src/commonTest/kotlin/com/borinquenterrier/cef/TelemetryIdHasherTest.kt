package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TelemetryIdHasherTest : StringSpec({
    "same input produces same hash" {
        TelemetryIdHasher.hash("primary") shouldBe TelemetryIdHasher.hash("primary")
    }

    "different input produces different hash" {
        TelemetryIdHasher.hash("walter@gmail.com") shouldNotBe TelemetryIdHasher.hash("abc123@group.calendar.google.com")
    }

    "hash never contains the original value" {
        val emailLikeId = "walter@gmail.com"
        TelemetryIdHasher.hash(emailLikeId) shouldNotBe emailLikeId
    }

    "hash is a short, constant-length hex string" {
        val hash = TelemetryIdHasher.hash("Syllabus - BDAN 250.pdf")
        hash.length shouldBe 12
        hash.all { it.isDigit() || it in 'a'..'f' } shouldBe true
    }

    "empty input produces a stable non-empty hash" {
        val hash1 = TelemetryIdHasher.hash("")
        val hash2 = TelemetryIdHasher.hash("")
        hash1 shouldNotBe ""
        hash1 shouldBe hash2
    }
})
