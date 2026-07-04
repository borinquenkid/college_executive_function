package com.borinquenterrier.cef

import kotlin.random.Random

/** Minimal JSON string escaping for hand-built JSON payloads (OTLP exports, SSE events). */
fun String.escapeJsonString(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

/** Random lowercase hex id, e.g. for OTEL trace/span ids or SSE run ids. */
fun randomHexId(bytes: Int): String = buildString(bytes * 2) {
    repeat(bytes) { append(Random.Default.nextInt(256).toString(16).padStart(2, '0')) }
}
