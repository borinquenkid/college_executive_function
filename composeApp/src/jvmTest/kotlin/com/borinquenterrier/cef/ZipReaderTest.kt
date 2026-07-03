package com.borinquenterrier.cef

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

/**
 * Verifies [ZipReader] against real archives built with java.util.zip.ZipOutputStream — an
 * independent, standard ZIP writer — not just re-checking against itself.
 */
class ZipReaderTest {

    private fun buildZip(entries: Map<String, String>, method: Int = ZipEntry.DEFLATED): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zos ->
            zos.setMethod(method)
            for ((name, content) in entries) {
                val bytes = content.encodeToByteArray()
                val entry = ZipEntry(name)
                if (method == ZipEntry.STORED) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    val crc = java.util.zip.CRC32()
                    crc.update(bytes)
                    entry.crc = crc.value
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    @Test
    fun `extracts a single deflated entry matching a docx-like layout`() {
        val zip = buildZip(mapOf("word/document.xml" to "<w:document>hello</w:document>"))
        val extracted = ZipReader.readEntry(zip, "word/document.xml")
        assertContentEquals("<w:document>hello</w:document>".encodeToByteArray(), extracted)
    }

    @Test
    fun `extracts the right entry among several, ignoring the others`() {
        val zip = buildZip(
            mapOf(
                "[Content_Types].xml" to "<Types/>",
                "word/document.xml" to "<w:document>the real content</w:document>",
                "word/styles.xml" to "<styles/>"
            )
        )
        val extracted = ZipReader.readEntry(zip, "word/document.xml")
        assertContentEquals("<w:document>the real content</w:document>".encodeToByteArray(), extracted)
    }

    @Test
    fun `extracts a stored (uncompressed) entry`() {
        val zip = buildZip(mapOf("word/document.xml" to "plain stored content"), method = ZipEntry.STORED)
        val extracted = ZipReader.readEntry(zip, "word/document.xml")
        assertContentEquals("plain stored content".encodeToByteArray(), extracted)
    }

    @Test
    fun `extracts a large repetitive entry spanning multiple deflate blocks`() {
        val content = "Deliverable: Essay draft due Monday. ".repeat(5000)
        val zip = buildZip(mapOf("word/document.xml" to content))
        val extracted = ZipReader.readEntry(zip, "word/document.xml")
        assertContentEquals(content.encodeToByteArray(), extracted)
    }

    @Test
    fun `returns null for an entry that does not exist`() {
        val zip = buildZip(mapOf("word/document.xml" to "content"))
        assertNull(ZipReader.readEntry(zip, "word/missing.xml"))
    }

    @Test
    fun `throws on a non-ZIP byte array`() {
        assertFailsWith<ZipReader.ZipException> {
            ZipReader.readEntry("not a zip file at all".encodeToByteArray(), "word/document.xml")
        }
    }
}
