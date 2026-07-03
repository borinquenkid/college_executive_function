package com.borinquenterrier.cef

import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * Verifies the pure-Kotlin [Inflate] decompressor against real compressed output from the
 * JVM's own java.util.zip.Deflater (raw/nowrap mode, matching what ZIP entries use) — an
 * independent, standard implementation, not just re-checking Inflate against itself.
 */
class InflateTest {

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, /* nowrap = */ true)
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(input.size * 2 + 128)
        val compressedLength = deflater.deflate(buffer)
        deflater.end()
        return buffer.copyOf(compressedLength)
    }

    private fun assertRoundTrips(original: ByteArray) {
        val compressed = deflateRaw(original)
        val decompressed = Inflate.inflate(compressed)
        assertContentEquals(original, decompressed)
    }

    @Test
    fun `round-trips an empty input`() {
        assertRoundTrips(ByteArray(0))
    }

    @Test
    fun `round-trips a short ascii string`() {
        assertRoundTrips("hello world".encodeToByteArray())
    }

    @Test
    fun `round-trips highly repetitive text (exercises back-references)`() {
        assertRoundTrips("the quick brown fox jumps over the lazy dog. ".repeat(200).encodeToByteArray())
    }

    @Test
    fun `round-trips a small DOCX-like XML fragment`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>Assignment 1 due Friday, October 3rd.</w:t></w:r></w:p>
                <w:p><w:r><w:t>Midterm exam: October 15th, in class.</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        assertRoundTrips(xml.encodeToByteArray())
    }

    @Test
    fun `round-trips random binary data (low compressibility, exercises stored and dynamic blocks)`() {
        assertRoundTrips(Random(42).nextBytes(5000))
    }

    @Test
    fun `round-trips a large text blob spanning multiple blocks`() {
        val sb = StringBuilder()
        repeat(20000) { sb.append("Lorem ipsum dolor sit amet, line $it.\n") }
        assertRoundTrips(sb.toString().encodeToByteArray())
    }

    @Test
    fun `throws on a reserved (invalid) block type rather than corrupting silently`() {
        // bit0 (final) = 0, bits1-2 (block type, LSB-first) = 1,1 -> reserved type 3 -> byte 0b00000110
        assertFailsWith<Inflate.InflateException> {
            Inflate.inflate(byteArrayOf(0x06))
        }
    }
}
