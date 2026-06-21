package com.borinquenterrier.cef

import java.io.File

actual class AppEnv actual constructor() {

    private val dotEnv: Map<String, String> by lazy { parseDotEnv() }

    actual constructor(dotEnvOverride: Map<String, String>) : this() {
        _dotEnvOverride = dotEnvOverride
    }

    // null = use lazy parseDotEnv(); non-null = use the override map (test mode)
    private var _dotEnvOverride: Map<String, String>? = null

    actual fun get(key: String): String? =
        _dotEnvOverride?.get(key)?.takeIf { it.isNotBlank() }
            ?: if (_dotEnvOverride != null) null  // in override mode: skip env vars
            else System.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: System.getenv(key)?.takeIf { it.isNotBlank() }
                ?: dotEnv[key]?.takeIf { it.isNotBlank() }

    private fun parseDotEnv(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        // Search CWD then parent — covers `./gradlew run` (CWD = project root)
        // and IDE Gradle runs (CWD = composeApp/).
        val file = listOf(".", "..").map { File(it, ".env") }.firstOrNull { it.exists() } ?: return map
        try {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val eq = trimmed.indexOf('=')
                if (eq > 0) map[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
            }
        } catch (e: Exception) {
            println("[AppEnv] Failed to parse .env: ${e.message}")
        }
        return map
    }
}
