package com.borinquenterrier.cef

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

@OptIn(ExperimentalNativeApi::class)
actual fun installGlobalCrashHandler() {
    setUnhandledExceptionHook { throwable ->
        try {
            AppTracer.current.recordFatal(throwable, mapOf("os" to "ios"))
        } catch (_: Throwable) {
            // Never mask the original crash.
        }
        // terminateWithUnhandledException keeps the runtime's default terminate behaviour
        // (stderr dump + abort) so symbolicated crash logs / Xcode Organizer still work —
        // a hook that doesn't terminate leaves the exception "handled" and the app frozen.
        terminateWithUnhandledException(throwable)
    }
}
