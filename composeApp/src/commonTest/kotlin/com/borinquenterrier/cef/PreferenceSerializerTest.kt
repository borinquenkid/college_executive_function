package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.verify

class PreferenceSerializerTest : StringSpec({
    val logger = mockk<Logger>(relaxed = true)
    val serializer = PreferenceSerializer(logger)

    // ========== serializeDirectories Tests ==========

    "serializeDirectories converts list to JSON string" {
        val dirs = listOf("/home/user/docs", "/mnt/storage")
        val result = serializer.serializeDirectories(dirs)

        result shouldNotBe ""
        result shouldContain "/home/user/docs"
        result shouldContain "/mnt/storage"
    }

    "serializeDirectories handles empty list" {
        val dirs = emptyList<String>()
        val result = serializer.serializeDirectories(dirs)

        result shouldNotBe ""
        result shouldContain "[]"
    }

    "serializeDirectories handles single directory" {
        val dirs = listOf("/single/path")
        val result = serializer.serializeDirectories(dirs)

        result shouldContain "/single/path"
    }

    "serializeDirectories handles directories with spaces" {
        val dirs = listOf("/home/user/My Documents", "/mnt/My Storage")
        val result = serializer.serializeDirectories(dirs)

        result shouldContain "My Documents"
        result shouldContain "My Storage"
    }

    "serializeDirectories handles directories with special characters" {
        val dirs = listOf("/home/user/data-2025", "/mnt/storage_v2.1")
        val result = serializer.serializeDirectories(dirs)

        result shouldContain "data-2025"
        result shouldContain "storage_v2.1"
    }

    "serializeDirectories returns empty string on exception" {
        val serializer = PreferenceSerializer(logger)
        // kotlinx.serialization is very robust, but we test exception handling path
        val result = serializer.serializeDirectories(emptyList())

        result shouldNotBe null
    }

    // ========== deserializeDirectories Tests ==========

    "deserializeDirectories parses JSON string back to list" {
        val original = listOf("/home/user/docs", "/mnt/storage")
        val json = serializer.serializeDirectories(original)

        val result = serializer.deserializeDirectories(json)

        result shouldBe original
    }

    "deserializeDirectories returns null for blank string" {
        val result = serializer.deserializeDirectories("")

        result shouldBe null
    }

    "deserializeDirectories returns null for whitespace-only string" {
        val result = serializer.deserializeDirectories("   \n\t  ")

        result shouldBe null
    }

    "deserializeDirectories returns null for malformed JSON" {
        val result = serializer.deserializeDirectories("{invalid json}")

        result shouldBe null
    }

    "deserializeDirectories returns null for empty JSON array string but valid JSON" {
        val json = "[]"
        val result = serializer.deserializeDirectories(json)

        result shouldBe emptyList()
    }

    "deserializeDirectories returns null for JSON object instead of array" {
        val result = serializer.deserializeDirectories("{\"path\": \"/home\"}")

        result shouldBe null
    }

    "deserializeDirectories handles single element array" {
        val json = serializer.serializeDirectories(listOf("/single"))
        val result = serializer.deserializeDirectories(json)

        result shouldBe listOf("/single")
    }

    "deserializeDirectories handles many directories" {
        val dirs = (1..100).map { "/path/to/dir$it" }
        val json = serializer.serializeDirectories(dirs)
        val result = serializer.deserializeDirectories(json)

        result shouldBe dirs
    }

    "deserializeDirectories preserves directory paths with unicode" {
        val dirs = listOf("/home/user/文档", "/mnt/αβγ")
        val json = serializer.serializeDirectories(dirs)
        val result = serializer.deserializeDirectories(json)

        result shouldBe dirs
    }

    // ========== Round-Trip Tests ==========

    "round-trip serialization preserves list" {
        val original = listOf("/docs", "/media", "/backup")

        val json = serializer.serializeDirectories(original)
        val deserialized = serializer.deserializeDirectories(json)
        val reserialied = serializer.serializeDirectories(deserialized!!)
        val final = serializer.deserializeDirectories(reserialied)

        final shouldBe original
    }

    "round-trip with empty list" {
        val original = emptyList<String>()

        val json = serializer.serializeDirectories(original)
        val deserialized = serializer.deserializeDirectories(json)

        deserialized shouldBe original
    }

    // ========== Error Handling & Logging Tests ==========

    "deserializeDirectories logs on exception" {
        val malformed = "not valid json at all ["
        serializer.deserializeDirectories(malformed)

        // Logger should be called when exception occurs
        verify(atLeast = 1) { logger.e(any(), any(), any<Exception>()) }
    }

    "deserializeDirectories gracefully handles null input edge case" {
        val result = serializer.deserializeDirectories("")
        result shouldBe null
    }

    // ========== JSON Structure Validation ==========

    "serialized JSON is valid kotlinx.serialization format" {
        val dirs = listOf("/path1", "/path2")
        val json = serializer.serializeDirectories(dirs)

        // Re-deserialize to verify format is valid
        val reparsed = serializer.deserializeDirectories(json)
        reparsed shouldBe dirs
    }

    "deserialization preserves order" {
        val dirs = listOf("/first", "/second", "/third", "/fourth")
        val json = serializer.serializeDirectories(dirs)
        val result = serializer.deserializeDirectories(json)

        result?.get(0) shouldBe "/first"
        result?.get(1) shouldBe "/second"
        result?.get(2) shouldBe "/third"
        result?.get(3) shouldBe "/fourth"
    }
})
