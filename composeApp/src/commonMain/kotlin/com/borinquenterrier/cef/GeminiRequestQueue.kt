package com.borinquenterrier.cef

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single-consumer in-memory queue for Gemini API requests.
 *
 * All callers suspend until their slot is reached. The drain coroutine
 * executes one request at a time and then waits at least [intervalMs] before
 * pulling the next — keeping outbound rate at most 60_000 / intervalMs RPM.
 *
 * Default: 6 000 ms = 10 RPM, safely below the 15 RPM free-tier ceiling.
 * Any retries internal to the enqueued block count as part of that one slot,
 * so a retry storm in one request still only occupies one queue position.
 *
 * Rate-limit feedback: call [notifyRateLimit] from inside a slot when the API
 * returns a 429. The post-slot delay extends to max(intervalMs, reported delay),
 * so the queue automatically backs off when models are saturated.
 *
 * [pendingCount] is observable: UI can derive a processing-time estimate as
 * pendingCount × (intervalMs + avgResponseMs).
 */
class GeminiRequestQueue(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 6_000L

        private val sharedInstance = GeminiRequestQueue()

        fun shared(): GeminiRequestQueue = sharedInstance
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val channel = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    var isBypassed: Boolean = false

    /** Highest rate-limit delay reported during the current slot; reset after each slot. */
    private var extendedIntervalMs: Long = 0L

    /**
     * Called from inside a slot when the API returns a 429.
     * Extends the post-slot delay to at least [delayMs] so the next pull
     * respects the window the API actually reported.
     */
    fun notifyRateLimit(delayMs: Long) {
        if (delayMs > extendedIntervalMs) extendedIntervalMs = delayMs
    }

    fun resetExtendedInterval() {
        extendedIntervalMs = 0L
    }

    init {
        scope.launch {
            for (work in channel) {
                work()
                _pendingCount.value = maxOf(0, _pendingCount.value - 1)
                if (!isBypassed && intervalMs > 0) {
                    val effective = maxOf(intervalMs, extendedIntervalMs)
                    extendedIntervalMs = 0L
                    delay(effective)
                }
            }
        }
    }

    suspend fun <T> enqueue(block: suspend () -> T): T {
        if (isBypassed) return block()
        val deferred = CompletableDeferred<T>()
        _pendingCount.value++
        channel.send {
            try {
                deferred.complete(block())
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred.await()
    }

    fun estimatedRemainingSeconds(avgResponseMs: Long = 3_000L): Int {
        val pending = _pendingCount.value
        if (pending == 0) return 0
        return ((pending.toLong() * (intervalMs + avgResponseMs)) / 1_000L).toInt()
    }
}
