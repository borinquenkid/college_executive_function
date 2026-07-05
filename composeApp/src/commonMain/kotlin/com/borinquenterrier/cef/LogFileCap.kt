package com.borinquenterrier.cef

// Android and iOS already capped their debug log files independently before desktop grew one
// (HARD-6, ROADMAP.md Phase 10); this is the one shared implementation all three platforms call
// into, rather than each carrying its own copy of the same trim logic.
const val MAX_LOG_FILE_BYTES = 500_000

fun capLogContent(content: String, maxBytes: Int = MAX_LOG_FILE_BYTES): String =
    if (content.length > maxBytes) content.takeLast(maxBytes) else content
