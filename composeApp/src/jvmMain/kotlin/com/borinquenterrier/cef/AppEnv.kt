package com.borinquenterrier.cef

import java.io.File

/**
 * Single source of truth for runtime configuration on JVM.
 *
 * Priority order (first non-null wins):
 *   1. JVM system properties  (-Dkey=value)
 *   2. OS environment variables
 *   3. .env file in the working directory
 *
 * The .env file is parsed once and cached. Keys with no value on any tier return null.
 */
object AppEnv {

    private val dotEnv: Map<String, String> by lazy { parseDotEnv() }

    fun get(key: String): String? =
        System.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: System.getenv(key)?.takeIf { it.isNotBlank() }
            ?: dotEnv[key]?.takeIf { it.isNotBlank() }

    private fun parseDotEnv(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val file = File(".env").takeIf { it.exists() } ?: return map
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
