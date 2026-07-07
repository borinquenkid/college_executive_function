package com.borinquenterrier.cef

import okio.ByteString.Companion.encodeUtf8

object ContentHasher {
    fun hash(fragments: List<SourceFragment>): String {
        val combinedText = fragments.joinToString("\n\n") { it.text }
        return hashString(combinedText)
    }

    /** SHA-256 hex of an arbitrary string. Used for deterministic, content-derived ids. */
    fun hashString(value: String): String = value.encodeUtf8().sha256().hex()
}
