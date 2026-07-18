package com.borinquenterrier.cef

actual fun installGlobalCrashHandler() {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            AppTracer.current.recordFatal(
                throwable,
                mapOf(
                    "thread.name" to thread.name,
                    "os" to "android",
                    "os.version" to android.os.Build.VERSION.SDK_INT.toString()
                )
            )
        } catch (_: Throwable) {
            // Telemetry must never make a crash worse or mask the original exception.
        }
        // Preserve default platform behaviour (Logcat dump, "App has stopped", Play Vitals
        // crash reporting) — never swallow the crash, only observe it.
        if (previousHandler != null) {
            previousHandler.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }
}
