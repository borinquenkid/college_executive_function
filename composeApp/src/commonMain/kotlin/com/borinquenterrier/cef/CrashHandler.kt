package com.borinquenterrier.cef

/**
 * Installs a platform-level uncaught-exception hook that reports the crash via
 * AppTracer.current.recordFatal(...) before the process terminates, so a real crash on a
 * deployed phone is traceable in OpenObserve instead of only spans inside instrumented
 * span{} blocks. Must be called as early as possible in app startup.
 */
expect fun installGlobalCrashHandler()
