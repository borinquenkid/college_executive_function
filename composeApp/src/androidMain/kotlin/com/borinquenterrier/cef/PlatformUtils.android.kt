package com.borinquenterrier.cef

actual object PlatformUtils {
    // Requires an Android Context not available at this call site; browser launch is handled by the UI layer.
    @Suppress("EmptyMethod", "UNUSED_PARAMETER")
    actual fun openBrowser(url: String) {}
}
