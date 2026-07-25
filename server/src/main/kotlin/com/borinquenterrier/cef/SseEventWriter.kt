package com.borinquenterrier.cef

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay
import kotlin.time.Clock

/** Shared AG-UI SSE event framing (see SPEC.md 4.2) for routes that respondBytesWriter a
 *  text/event-stream body — used by /api/agent/stream and /api/events/{id}/decompose/stream. */
class SseEventWriter(private val channel: ByteWriteChannel) {
    suspend fun emit(type: String, dataJson: String) {
        channel.writeStringUtf8("event: message\n")
        channel.writeStringUtf8(
            "data: {\"type\":\"$type\",\"timestamp\":${Clock.System.now().toEpochMilliseconds()}," +
                "\"data\":$dataJson}\n\n"
        )
        channel.flush()
    }

    /** Streams [text] as TEXT_MESSAGE_START, then one TEXT_MESSAGE_DELTA per word plus its
     *  trailing whitespace (so concatenating every delta's "text" reproduces [text] exactly),
     *  then TEXT_MESSAGE_END — see SPEC.md 4.2's documented word-by-word contract. */
    suspend fun emitTextWordByWord(text: String, delayMs: Long = 25) {
        emit("TEXT_MESSAGE_START", "{}")
        Regex("\\S+\\s*").findAll(text).forEach { match ->
            emit("TEXT_MESSAGE_DELTA", "{\"text\":\"${match.value.escapeJsonString()}\"}")
            delay(delayMs)
        }
        emit("TEXT_MESSAGE_END", "{}")
    }
}
