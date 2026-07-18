package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cross-platform entry point Android/iOS lifecycle callbacks call to flush in-flight span/event
 * exports before the app backgrounds. Non-blocking at the call site — it launches its own
 * background coroutine, so it's always safe to call directly from onPause()/
 * sceneDidEnterBackground without UI jank or ANR risk. The bounded wait happens on this
 * object's own background thread, not the caller's.
 */
object TracerLifecycle {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun flush(timeoutMillis: Long = 2_000) {
        scope.launch { AppTracer.current.flush(timeoutMillis) }
    }
}
