package com.borinquenterrier.cef

/**
 * Extracts plain text from a .docx (a ZIP container) via [ZipReader]/[Inflate]. Pure common
 * Kotlin, so it's directly testable on JVM against a real .docx fixture without needing a
 * device or simulator — the same logic runs unchanged on iOS through DocxReader.ios.kt.
 */
object DocxContentExtractor {

    /** Returns the extracted plain text, or null if word/document.xml isn't present
     *  (not a valid DOCX). */
    fun extractPlainText(archive: ByteArray): String? {
        val documentXmlBytes = ZipReader.readEntry(archive, "word/document.xml") ?: return null
        val content = documentXmlBytes.decodeToString()
        return content
            .replace(Regex("<w:p[\\s\\S]*?>"), "\n")
            .replace(Regex("<.*?>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
