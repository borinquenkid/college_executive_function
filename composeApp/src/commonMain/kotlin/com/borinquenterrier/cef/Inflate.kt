package com.borinquenterrier.cef

/**
 * A pure-Kotlin RFC 1951 (DEFLATE) decompressor.
 *
 * Exists because Kotlin/Native's iOS platform bindings don't expose Apple's Compression
 * framework out of the box (no `platform.Compression.*`), and binding libcompression
 * ourselves would need a custom cinterop .def file — a build-config change, not a plain
 * Kotlin edit. This is a self-contained algorithm instead: no cinterop, no external
 * packages, identical on every platform (Android/JVM already have java.util.zip; this is
 * what makes DOCX/ZIP extraction possible on iOS too — see [ZipReader]/[DocxReader.ios.kt]).
 *
 * Ported from the structure of Mark Adler's reference "puff" decompressor (public domain),
 * simplified to only what a ZIP entry's raw deflate stream needs.
 */
object Inflate {

    class InflateException(message: String) : Exception(message)

    /** Decompresses a raw (headerless) DEFLATE stream. */
    fun inflate(input: ByteArray): ByteArray {
        val reader = BitReader(input)
        val output = ByteArrayBuilder()

        while (true) {
            val isFinal = reader.readBits(1) == 1
            when (val blockType = reader.readBits(2)) {
                0 -> inflateStored(reader, output)
                1 -> inflateHuffman(reader, output, FIXED_LITERAL_TABLE, FIXED_DISTANCE_TABLE)
                2 -> {
                    val (literalTable, distanceTable) = readDynamicTables(reader)
                    inflateHuffman(reader, output, literalTable, distanceTable)
                }
                else -> throw InflateException("Invalid DEFLATE block type: $blockType")
            }
            if (isFinal) break
        }

        return output.toByteArray()
    }

    private fun inflateStored(reader: BitReader, output: ByteArrayBuilder) {
        reader.alignToByte()
        val len = reader.readAlignedUInt16()
        val nlen = reader.readAlignedUInt16()
        if (len != nlen.inv() and 0xFFFF) {
            throw InflateException("Stored block LEN/NLEN mismatch")
        }
        repeat(len) { output.append(reader.readAlignedByte()) }
    }

    private fun inflateHuffman(
        reader: BitReader,
        output: ByteArrayBuilder,
        literalTable: HuffmanTable,
        distanceTable: HuffmanTable
    ) {
        while (true) {
            val symbol = literalTable.decode(reader)
            when {
                symbol < 256 -> output.append(symbol.toByte())
                symbol == 256 -> return // end of block
                else -> {
                    val lengthIndex = symbol - 257
                    if (lengthIndex >= LENGTH_BASE.size) throw InflateException("Invalid length symbol: $symbol")
                    val length = LENGTH_BASE[lengthIndex] + reader.readBits(LENGTH_EXTRA_BITS[lengthIndex])

                    val distSymbol = distanceTable.decode(reader)
                    if (distSymbol >= DISTANCE_BASE.size) throw InflateException("Invalid distance symbol: $distSymbol")
                    val distance = DISTANCE_BASE[distSymbol] + reader.readBits(DISTANCE_EXTRA_BITS[distSymbol])

                    output.copyFromDistance(distance, length)
                }
            }
        }
    }

    private fun readDynamicTables(reader: BitReader): Pair<HuffmanTable, HuffmanTable> {
        val numLiteralCodes = reader.readBits(5) + 257
        val numDistanceCodes = reader.readBits(5) + 1
        val numCodeLengthCodes = reader.readBits(4) + 4

        val codeLengthOrder = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val codeLengthCodeLengths = IntArray(19)
        for (i in 0 until numCodeLengthCodes) {
            codeLengthCodeLengths[codeLengthOrder[i]] = reader.readBits(3)
        }
        val codeLengthTable = HuffmanTable(codeLengthCodeLengths)

        val allLengths = IntArray(numLiteralCodes + numDistanceCodes)
        var i = 0
        while (i < allLengths.size) {
            when (val symbol = codeLengthTable.decode(reader)) {
                in 0..15 -> allLengths[i++] = symbol
                16 -> {
                    val repeatCount = reader.readBits(2) + 3
                    if (i == 0) throw InflateException("Repeat code with no previous length")
                    val previous = allLengths[i - 1]
                    repeat(repeatCount) { allLengths[i++] = previous }
                }
                17 -> {
                    val repeatCount = reader.readBits(3) + 3
                    repeat(repeatCount) { allLengths[i++] = 0 }
                }
                18 -> {
                    val repeatCount = reader.readBits(7) + 11
                    repeat(repeatCount) { allLengths[i++] = 0 }
                }
                else -> throw InflateException("Invalid code length symbol: $symbol")
            }
        }

        val literalLengths = allLengths.copyOfRange(0, numLiteralCodes)
        val distanceLengths = allLengths.copyOfRange(numLiteralCodes, allLengths.size)
        return HuffmanTable(literalLengths) to HuffmanTable(distanceLengths)
    }

    /** Canonical Huffman decode table built from per-symbol code lengths (RFC 1951 §3.2.2). */
    private class HuffmanTable(codeLengths: IntArray) {
        // Maps (bit-length, code value) -> symbol, decoded MSB-first per the canonical scheme.
        private val symbolsByLength: Array<MutableMap<Int, Int>>
        private val maxLength: Int

        init {
            maxLength = codeLengths.maxOrNull() ?: 0
            symbolsByLength = Array(maxLength + 1) { mutableMapOf() }

            val lengthCounts = IntArray(maxLength + 1)
            for (len in codeLengths) if (len > 0) lengthCounts[len]++

            val nextCode = IntArray(maxLength + 1)
            var code = 0
            for (len in 1..maxLength) {
                code = (code + lengthCounts[len - 1]) shl 1
                nextCode[len] = code
            }

            for (symbol in codeLengths.indices) {
                val len = codeLengths[symbol]
                if (len > 0) {
                    symbolsByLength[len][nextCode[len]] = symbol
                    nextCode[len]++
                }
            }
        }

        fun decode(reader: BitReader): Int {
            var code = 0
            for (len in 1..maxLength) {
                code = (code shl 1) or reader.readBitMsbFirst()
                symbolsByLength[len][code]?.let { return it }
            }
            throw InflateException("No matching Huffman code found")
        }
    }

    private val FIXED_LITERAL_TABLE = HuffmanTable(IntArray(288) { symbol ->
        when (symbol) {
            in 0..143 -> 8
            in 144..255 -> 9
            in 256..279 -> 7
            else -> 8
        }
    })
    private val FIXED_DISTANCE_TABLE = HuffmanTable(IntArray(30) { 5 })

    private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LENGTH_EXTRA_BITS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DISTANCE_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DISTANCE_EXTRA_BITS = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

    /** Reads DEFLATE's bit-packed stream: LSB-first within each byte, per RFC 1951 §3.1.1. */
    private class BitReader(private val data: ByteArray) {
        private var bytePos = 0
        private var bitPos = 0 // 0..7, next bit to read within data[bytePos]

        fun readBits(count: Int): Int {
            var result = 0
            for (i in 0 until count) {
                result = result or (readBitLsbFirst() shl i)
            }
            return result
        }

        /** Huffman codes are packed MSB-first per symbol (RFC 1951 §3.2.2), unlike everything else. */
        fun readBitMsbFirst(): Int = readBitLsbFirst()

        private fun readBitLsbFirst(): Int {
            if (bytePos >= data.size) throw InflateException("Unexpected end of DEFLATE stream")
            val bit = (data[bytePos].toInt() ushr bitPos) and 1
            bitPos++
            if (bitPos == 8) {
                bitPos = 0
                bytePos++
            }
            return bit
        }

        fun alignToByte() {
            if (bitPos != 0) {
                bitPos = 0
                bytePos++
            }
        }

        fun readAlignedByte(): Byte {
            if (bytePos >= data.size) throw InflateException("Unexpected end of DEFLATE stream")
            return data[bytePos++]
        }

        fun readAlignedUInt16(): Int {
            val lo = readAlignedByte().toInt() and 0xFF
            val hi = readAlignedByte().toInt() and 0xFF
            return lo or (hi shl 8)
        }
    }

    /** Minimal growable byte buffer supporting DEFLATE's back-reference copies. */
    private class ByteArrayBuilder {
        private var buffer = ByteArray(4096)
        private var size = 0

        fun append(byte: Byte) {
            ensureCapacity(size + 1)
            buffer[size++] = byte
        }

        fun copyFromDistance(distance: Int, length: Int) {
            if (distance > size) throw InflateException("Back-reference distance exceeds output size")
            ensureCapacity(size + length)
            var readPos = size - distance
            repeat(length) {
                buffer[size++] = buffer[readPos++]
            }
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity <= buffer.size) return
            var newCapacity = buffer.size * 2
            while (newCapacity < minCapacity) newCapacity *= 2
            buffer = buffer.copyOf(newCapacity)
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
