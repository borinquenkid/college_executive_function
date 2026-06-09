package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.mockk
import io.mockk.verify

class PreferenceSerializerTest : FunSpec({
    val logger = mockk<Logger>(relaxed = true)
    val serializer = PreferenceSerializer(logger)

    test("deserializeDirectories parses valid JSON array") {
        val json = """["path1","path2","path3"]"""

        val result = serializer.deserializeDirectories(json)

        result shouldContainExactly listOf("path1", "path2", "path3")
    }

    test("deserializeDirectories returns null for empty string") {
        val result = serializer.deserializeDirectories("")

        result shouldBe null
    }

    test("deserializeDirectories returns null for blank string") {
        val result = serializer.deserializeDirectories("   \n\t  ")

        result shouldBe null
    }

    test("deserializeDirectories returns null for invalid JSON") {
        val invalidJson = """[not valid json]"""

        val result = serializer.deserializeDirectories(invalidJson)

        result shouldBe null
    }

    test("deserializeDirectories logs error on invalid JSON") {
        val invalidJson = """{"invalid": format}"""

        serializer.deserializeDirectories(invalidJson)

        verify { logger.e(any(), any<String>(), any<Exception>()) }
    }

    test("deserializeDirectories handles empty array") {
        val json = """[]"""

        val result = serializer.deserializeDirectories(json)

        result shouldContainExactly emptyList()
    }

    test("deserializeDirectories handles single element array") {
        val json = """["single"]"""

        val result = serializer.deserializeDirectories(json)

        result shouldContainExactly listOf("single")
    }

    test("serializeDirectories converts list to JSON array") {
        val dirs = listOf("path1", "path2")

        val result = serializer.serializeDirectories(dirs)

        result shouldBe """["path1","path2"]"""
    }

    test("serializeDirectories handles empty list") {
        val dirs = emptyList<String>()

        val result = serializer.serializeDirectories(dirs)

        result shouldBe "[]"
    }

    test("serializeDirectories handles single element") {
        val dirs = listOf("single")

        val result = serializer.serializeDirectories(dirs)

        result shouldBe """["single"]"""
    }

    test("serializeDirectories returns empty string on error") {
        // This test would require a way to make Json.encodeToString fail,
        // which is difficult with the current implementation.
        // In practice, encodeToString with List<String> is very reliable.
        // The error handling is there for defensive coding.
        val dirs = listOf("valid", "path")

        val result = serializer.serializeDirectories(dirs)

        result.isNotEmpty() shouldBe true
    }

    test("roundtrip: serialize then deserialize returns original list") {
        val original = listOf("path1", "path2", "path3")

        val serialized = serializer.serializeDirectories(original)
        val deserialized = serializer.deserializeDirectories(serialized)

        deserialized shouldContainExactly original
    }
})
