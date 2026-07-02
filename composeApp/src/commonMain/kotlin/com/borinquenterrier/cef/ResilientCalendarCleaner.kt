package com.borinquenterrier.cef

import kotlinx.coroutines.delay

/**
 * Deletes every event on a remote calendar without giving up on the first hiccup — the failure the
 * old clearCalendar hit, where a single 403 "Rate Limit Exceeded" mid-delete aborted the whole
 * reset and left the calendar half-cleared.
 *
 * For each event it retries a rate-limited delete with backoff, treats an already-gone event (410)
 * as success, and — critically — CONTINUES past any event it can't delete instead of throwing. The
 * returned [Result] reports how many were removed vs left behind, so a caller can re-run to finish.
 */
class ResilientCalendarCleaner(
    private val remoteRepo: RemoteCalendarRepository,
    private val logger: Logger? = null,
    private val maxRetries: Int = 3,
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {
    private val tag = "ResilientCalendarCleaner"

    data class Result(val deleted: Int, val failed: Int) {
        val allCleared: Boolean get() = failed == 0
    }

    suspend fun clear(calendarId: String): Result =
        AppTracer.current.span("calendar.resilient_clear", mapOf("calendar.id" to calendarId)) {
            val events = try {
                remoteRepo.getAllEvents(calendarId)
            } catch (e: Exception) {
                logger?.e(tag, "Could not list events to clear: ${e.message}", e)
                setAttribute("clear.list_failed", "true")
                return@span Result(deleted = 0, failed = 0)
            }
            var deleted = 0
            var failed = 0
            for (event in events) {
                val id = event.id ?: continue
                if (deleteWithRetry(id, calendarId)) deleted++ else failed++
            }
            setAttribute("clear.deleted", deleted.toLong())
            setAttribute("clear.failed", failed.toLong())
            Result(deleted, failed)
        }

    private suspend fun deleteWithRetry(eventId: String, calendarId: String): Boolean {
        var attempt = 0
        while (true) {
            try {
                remoteRepo.deleteEvent(eventId, calendarId)
                return true
            } catch (e: GoogleApiException) {
                if (e.statusCode == 410) return true // already deleted — success
                if (isRateLimit(e) && attempt < maxRetries) {
                    attempt++
                    delayFn(backoffMs(attempt))
                    continue
                }
                logger?.e(tag, "Skipping event $eventId after ${e.statusCode}: ${e.message}")
                return false
            } catch (e: Exception) {
                logger?.e(tag, "Skipping event $eventId: ${e.message}")
                return false
            }
        }
    }

    private fun isRateLimit(e: GoogleApiException): Boolean =
        e.statusCode == 429 ||
            (e.statusCode == 403 && e.responseBody.contains("rate limit", ignoreCase = true))

    private fun backoffMs(attempt: Int): Long = minOf(8000L, 500L * (1L shl (attempt - 1)))
}
