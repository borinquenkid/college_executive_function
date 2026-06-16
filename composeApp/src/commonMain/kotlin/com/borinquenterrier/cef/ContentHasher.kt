package com.borinquenterrier.cef

import okio.ByteString.Companion.encodeUtf8

object ContentHasher {
    fun hash(fragments: List<SourceFragment>): String {
        val combinedText = fragments.joinToString("\n\n") { it.text }
        return combinedText.encodeUtf8().sha256().hex()
    }
}
