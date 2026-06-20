package com.borinquenterrier.cef

object RetryCountdown {

    fun secondsRemaining(holdUntilMs: Long, nowMs: Long): Int? {
        val remaining = holdUntilMs - nowMs
        if (remaining <= 0) return null
        return ((remaining + 999) / 1000).toInt()
    }
}
