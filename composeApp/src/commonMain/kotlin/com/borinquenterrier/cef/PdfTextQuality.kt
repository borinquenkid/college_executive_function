package com.borinquenterrier.cef

/**
 * Heuristic for deciding whether a PDF is "image-only" (scanned pages with no embedded text
 * layer), which the text extractors return as (near-)empty. Such PDFs need the model's document
 * vision to recover their text. A text PDF yields hundreds of characters per page, so a very low
 * non-whitespace count across all fragments is a reliable signal.
 */
object PdfTextQuality {
    /** Below this many non-whitespace characters, treat the extraction as an image-only PDF. */
    const val MIN_MEANINGFUL_CHARS = 40

    fun meaningfulCharCount(fragments: List<SourceFragment>): Int =
        fragments.sumOf { fragment -> fragment.text.count { !it.isWhitespace() } }

    fun isImageOnly(fragments: List<SourceFragment>): Boolean =
        meaningfulCharCount(fragments) < MIN_MEANINGFUL_CHARS
}
