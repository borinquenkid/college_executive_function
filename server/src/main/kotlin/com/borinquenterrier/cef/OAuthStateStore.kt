package com.borinquenterrier.cef

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Short-lived state for OIDC-style login handshakes: the LTI 1.3 third-party-initiated-login
 * flow (state -> nonce) and the Google web OAuth authorization-code flow (state -> studentId).
 * In-memory is enough here — this is a single-process deployment (see DEPLOYMENT.md) and every
 * entry lives minutes, not for the life of a session.
 */
object OAuthStateStore {
    private data class Entry(val value: String, val expiresAtMillis: Long)
    private val states = ConcurrentHashMap<String, Entry>()

    fun create(value: String, ttl: kotlin.time.Duration = 5.minutes): String {
        sweep()
        val state = randomHexId(32)
        states[state] = Entry(value, Clock.System.now().toEpochMilliseconds() + ttl.inWholeMilliseconds)
        return state
    }

    /** Single-use: consuming removes the entry so a replayed state can't be reused. */
    fun consume(state: String): String? {
        val entry = states.remove(state) ?: return null
        if (entry.expiresAtMillis < Clock.System.now().toEpochMilliseconds()) return null
        return entry.value
    }

    private fun sweep() {
        val now = Clock.System.now().toEpochMilliseconds()
        states.entries.removeIf { it.value.expiresAtMillis < now }
    }
}
