package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

/**
 * Simple Logger utility that respect a "DEBUG_MODE" setting.
 */
class Logger(private val settings: Settings) {
    
    private val debugModeKey = "DEBUG_MODE"
    private var logFile: String? = null
    
    fun isDebugEnabled(): Boolean = settings.getBoolean(debugModeKey, isDebug)

    fun d(tag: String, message: String) {
        val log = "[$tag] DEBUG: $message"
        if (isDebugEnabled()) {
            println(log)
            appendToFile(log)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val log = "[$tag] ERROR: $message"
        println(log)
        appendToFile(log)
        throwable?.printStackTrace()
    }

    fun i(tag: String, message: String) {
        val log = "[$tag] INFO: $message"
        println(log)
        appendToFile(log)
    }

    private fun appendToFile(message: String) {
        if (!isDebugEnabled()) return
        writeLogToFile(message)
    }
}

expect fun writeLogToFile(message: String)

@androidx.compose.runtime.Composable
fun rememberLogger(): Logger {
    val settings = rememberSettings()
    return androidx.compose.runtime.remember(settings) { Logger(settings) }
}
