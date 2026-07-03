package com.borinquenterrier.cef

/**
 * A minimal ZIP-format reader that extracts one named entry's bytes from an in-memory archive.
 * Reads via the central directory (at the end of the archive) rather than scanning local file
 * headers linearly — the central directory's sizes are always accurate even for entries written
 * with a trailing data descriptor, unlike the local header copy of those fields.
 *
 * Supports the two compression methods a real-world DOCX/ZIP entry uses: stored (0) and
 * deflate (8, via [Inflate]). No zip64 support — not needed for syllabus-sized DOCX files.
 */
object ZipReader {

    class ZipException(message: String) : Exception(message)

    /** Extracts and (if needed) decompresses the named entry, or null if it isn't present. */
    fun readEntry(archive: ByteArray, entryName: String): ByteArray? {
        val eocdOffset = findEndOfCentralDirectory(archive)
            ?: throw ZipException("Not a valid ZIP archive: end-of-central-directory record not found")

        val centralDirOffset = readUInt32(archive, eocdOffset + 16).toInt()
        val entryCount = readUInt16(archive, eocdOffset + 10)

        var pos = centralDirOffset
        repeat(entryCount) {
            if (readUInt32(archive, pos) != CENTRAL_DIR_SIGNATURE) {
                throw ZipException("Malformed central directory entry at offset $pos")
            }
            val compressionMethod = readUInt16(archive, pos + 10)
            val compressedSize = readUInt32(archive, pos + 20).toInt()
            val fileNameLength = readUInt16(archive, pos + 28)
            val extraFieldLength = readUInt16(archive, pos + 30)
            val fileCommentLength = readUInt16(archive, pos + 32)
            val localHeaderOffset = readUInt32(archive, pos + 42).toInt()
            val name = archive.decodeToString(pos + 46, pos + 46 + fileNameLength)

            if (name == entryName) {
                return extractLocalEntry(archive, localHeaderOffset, compressionMethod, compressedSize)
            }

            pos += 46 + fileNameLength + extraFieldLength + fileCommentLength
        }
        return null
    }

    private fun extractLocalEntry(
        archive: ByteArray,
        localHeaderOffset: Int,
        compressionMethod: Int,
        compressedSize: Int
    ): ByteArray {
        if (readUInt32(archive, localHeaderOffset) != LOCAL_FILE_SIGNATURE) {
            throw ZipException("Malformed local file header at offset $localHeaderOffset")
        }
        val fileNameLength = readUInt16(archive, localHeaderOffset + 26)
        val extraFieldLength = readUInt16(archive, localHeaderOffset + 28)
        val dataStart = localHeaderOffset + 30 + fileNameLength + extraFieldLength
        val compressedBytes = archive.copyOfRange(dataStart, dataStart + compressedSize)

        return when (compressionMethod) {
            0 -> compressedBytes // stored, no compression
            8 -> Inflate.inflate(compressedBytes)
            else -> throw ZipException("Unsupported ZIP compression method: $compressionMethod")
        }
    }

    /** Scans backward from the end of the file for the EOCD signature — it's followed by a
     *  variable-length comment field, so its position isn't fixed. */
    private fun findEndOfCentralDirectory(archive: ByteArray): Int? {
        val minEocdSize = 22
        if (archive.size < minEocdSize) return null
        val searchStart = maxOf(0, archive.size - minEocdSize - 65536) // max comment length is 65535
        for (i in (archive.size - minEocdSize) downTo searchStart) {
            if (readUInt32(archive, i) == EOCD_SIGNATURE) return i
        }
        return null
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        val lo = data[offset].toInt() and 0xFF
        val hi = data[offset + 1].toInt() and 0xFF
        return lo or (hi shl 8)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 4) {
            result = result or ((data[offset + i].toLong() and 0xFF) shl (8 * i))
        }
        return result
    }

    private const val LOCAL_FILE_SIGNATURE = 0x04034b50L
    private const val CENTRAL_DIR_SIGNATURE = 0x02014b50L
    private const val EOCD_SIGNATURE = 0x06054b50L
}
