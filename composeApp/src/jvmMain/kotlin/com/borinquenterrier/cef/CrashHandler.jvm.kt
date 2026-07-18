package com.borinquenterrier.cef

actual fun installGlobalCrashHandler() {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            AppTracer.current.recordFatal(
                throwable,
                mapOf("thread.name" to thread.name, "os" to "desktop")
            )
        } catch (_: Throwable) {
            // Telemetry must never make a crash worse or mask the original exception.
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}
