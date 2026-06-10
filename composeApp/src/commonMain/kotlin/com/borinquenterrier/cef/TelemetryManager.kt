package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

/**
 * Centrally manages observability and telemetry counters across all platforms.
 */
class TelemetryManager(private val settings: Settings) {

    private val keyJsonErrors = "TELEMETRY_JSON_PARSING_ERRORS"
    private val keyRateLimitErrors = "TELEMETRY_RATE_LIMIT_ERRORS"
    private val keyCriticTotal = "TELEMETRY_CRITIC_TOTAL_PASSES"
    private val keyCriticModified = "TELEMETRY_CRITIC_MODIFIED_PASSES"

    fun logJsonError() {
        val current = settings.getInt(keyJsonErrors, 0)
        settings.putInt(keyJsonErrors, current + 1)
    }

    fun logRateLimitError() {
        val current = settings.getInt(keyRateLimitErrors, 0)
        settings.putInt(keyRateLimitErrors, current + 1)
    }

    fun logCriticPass(modified: Boolean) {
        val total = settings.getInt(keyCriticTotal, 0)
        settings.putInt(keyCriticTotal, total + 1)
        if (modified) {
            val mod = settings.getInt(keyCriticModified, 0)
            settings.putInt(keyCriticModified, mod + 1)
        }
    }

    fun getJsonErrors(): Int = settings.getInt(keyJsonErrors, 0)

    fun getRateLimitErrors(): Int = settings.getInt(keyRateLimitErrors, 0)

    fun getCriticTotal(): Int = settings.getInt(keyCriticTotal, 0)

    fun getCriticModified(): Int = settings.getInt(keyCriticModified, 0)

    fun getCriticTriggerRate(): Double {
        val total = getCriticTotal()
        if (total == 0) return 0.0
        return getCriticModified().toDouble() / total.toDouble()
    }

    fun clear() {
        settings.remove(keyJsonErrors)
        settings.remove(keyRateLimitErrors)
        settings.remove(keyCriticTotal)
        settings.remove(keyCriticModified)
    }
}
