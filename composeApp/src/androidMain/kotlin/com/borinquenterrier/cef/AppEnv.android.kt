package com.borinquenterrier.cef

actual class AppEnv actual constructor() {
    @Suppress("UNUSED_PARAMETER")
    actual constructor(dotEnvOverride: Map<String, String>) : this()
    @Suppress("UNUSED_PARAMETER", "SameReturnValue")
    actual fun get(key: String): String? = null
}
