package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock

/**
 * Manages polling schedule state, including last poll time and poll frequency logic.
 */
class PollScheduler(
    private val settings: Settings,
    private val logger: Logger
) {
    private val tag = "PollScheduler"
    private val lastPollTimeKey = "cef_harness_last_poll_time"
    private val twentyFourHoursMs = 24 * 60 * 60 * 1000L

    fun getLastPollTime(): Long {
        return settings.getLong(lastPollTimeKey, 0L)
    }

    fun setLastPollTime(timeMs: Long) {
        settings.putLong(lastPollTimeKey, timeMs)
    }

    /**
     * Checks if enough time has passed since the last poll to allow a new poll.
     * Returns true if a poll should proceed, false if it's too soon.
     */
    fun shouldPoll(force: Boolean = false): Boolean {
        if (force) return true

        val now = Clock.System.now().toEpochMilliseconds()
        val lastPoll = getLastPollTime()

        if (now - lastPoll < twentyFourHoursMs) {
            logger.d(tag, "24 hours have not passed since last poll. Skipping.")
            return false
        }
        return true
    }
}
