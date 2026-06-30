package com.borinquenterrier.cef

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single-consumer in-memory queue for Gemini API requests, **one instance per [PromptFamily]**.
 *
 * All callers suspend until their slot is reached. The drain coroutine executes one request at
 * a time and then waits before pulling the next. The post-slot delay is the largest of:
 *  - [intervalMs] — a configured floor (family queues use 0, i.e. "no floor");
 *  - the interval for the model actually used in the slot, reported via [noteModel]
 *    ([ModelRpm] turns the model's RPM ceiling into a minimum spacing — "pace for the model");
 *  - any rate-limit delay the server reported, via [notifyRateLimit].
 *
 * Because each prompt family has its own queue ([forFamily]), a timeout storm in one family
 * (e.g. a 42-batch syllabus extraction) only backs up that family's queue — chat,
 * categorization, and study-plan keep flowing. This is the deliberate replacement for the
 * single global queue, which coupled every prompt to the slowest one.
 *
 * Any retries internal to the enqueued block count as part of that one slot, so a retry storm
 * in one request still only occupies one queue position.
 *
 * [pendingCount] is observable per family; [pendingCountAll] aggregates across all families
 * for whole-pipeline UI progress.
 */
class GeminiRequestQueue(
    val family: PromptFamily? = null,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 6_000L

        /** One queue per prompt family, with no configured floor so the model paces it. */
        private val familyInstances: Map<PromptFamily, GeminiRequestQueue> =
            PromptFamily.entries.associateWith { GeminiRequestQueue(family = it, intervalMs = 0L) }

        private val aggregateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        /** The queue dedicated to [family]. */
        fun forFamily(family: PromptFamily): GeminiRequestQueue = familyInstances.getValue(family)

        /** All four family queues. */
        fun all(): List<GeminiRequestQueue> = familyInstances.values.toList()

        /** Pending requests across all families — drives whole-pipeline UI progress. */
        val pendingCountAll: StateFlow<Int> =
            combine(familyInstances.values.map { it.pendingCount }) { counts -> counts.sum() }
                .stateIn(aggregateScope, SharingStarted.Eagerly, 0)

        /** Estimated seconds to drain every family queue. */
        fun estimatedRemainingSecondsAll(avgResponseMs: Long = 3_000L): Int =
            familyInstances.values.sumOf { it.estimatedRemainingSeconds(avgResponseMs) }

        /** Bypass (run inline, no pacing) every family queue — test hook. */
        fun bypassAll(bypass: Boolean) {
            familyInstances.values.forEach { it.isBypassed = bypass }
        }

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

    /** Minimum spacing for the model used this slot, reported via [noteModel]; reset after each slot. */
    private var slotModelIntervalMs: Long = 0L

    /**
     * Report the model actually used in the current slot so the queue paces to that model's
     * RPM ceiling. Takes the slowest model noted within a slot (retries may switch models).
     */
    fun noteModel(modelName: String) {
        val iv = ModelRpm.intervalMsFor(modelName)
        if (iv > slotModelIntervalMs) slotModelIntervalMs = iv
    }

    /**
     * Called from inside a slot when the API returns a 429.
     * Extends the post-slot delay to at least [delayMs] so the next pull
     * respects the window the API actually reported.
     */
    fun notifyRateLimit(delayMs: Long) {
        if (delayMs > extendedIntervalMs) extendedIntervalMs = delayMs
    }

    /** Effective minimum spacing right now: configured floor, model pace, or reported rate-limit, whichever is largest. */
    val currentIntervalMs: Long get() = maxOf(intervalMs, slotModelIntervalMs)

    /** Post-slot delay the drain will apply next: floor vs model pace vs reported rate-limit. */
    fun computeEffectiveDelayMs(): Long = maxOf(intervalMs, slotModelIntervalMs, extendedIntervalMs)

    /** Clear per-slot pacing (model + rate-limit) back to the configured floor. */
    fun resetSlotPacing() {
        slotModelIntervalMs = 0L
        extendedIntervalMs = 0L
    }

    fun resetExtendedInterval() {
        extendedIntervalMs = 0L
    }

    init {
        scope.launch {
            for (work in channel) {
                work()
                _pendingCount.value = maxOf(0, _pendingCount.value - 1)
                if (!isBypassed) {
                    val effective = computeEffectiveDelayMs()
                    resetSlotPacing()
                    if (effective > 0) delay(effective)
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
