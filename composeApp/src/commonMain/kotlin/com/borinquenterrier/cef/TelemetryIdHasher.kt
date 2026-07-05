package com.borinquenterrier.cef

import okio.ByteString.Companion.encodeUtf8

/**
 * One-way hashes identifiers (calendar IDs, source titles, URLs) before they enter telemetry
 * spans. Preserves correlation value — the same input always hashes to the same output, so
 * repeated activity against one calendar/source is still visible in traces — without ever
 * transmitting or storing the actual value, which could be personally identifying (e.g. a
 * primary Google Calendar's ID is the user's email address).
 */
object TelemetryIdHasher {
    fun hash(value: String): String = value.encodeUtf8().sha256().hex().take(12)
}
