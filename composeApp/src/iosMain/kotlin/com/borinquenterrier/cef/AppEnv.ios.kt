package com.borinquenterrier.cef

actual class AppEnv actual constructor() {
    actual constructor(dotEnvOverride: Map<String, String>) : this()
    actual fun get(key: String): String? = null
}
