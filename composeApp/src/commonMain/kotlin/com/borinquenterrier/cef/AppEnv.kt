package com.borinquenterrier.cef

/**
 * Single source of truth for runtime configuration.
 *
 * Instantiated once in [DependencyContainer] and injected into classes that need
 * environment variables (tracer, auth service, etc.).  Each platform provides its
 * own actual implementation.
 *
 * Priority order (JVM): system property → OS env → .env file.
 * Other platforms return null for all keys unless overridden.
 *
 * Tests may use `AppEnv(emptyMap())` to suppress .env file reading.
 */
expect class AppEnv() {
    /** Secondary constructor: override the .env file values with a fixed map (useful in tests). */
    constructor(dotEnvOverride: Map<String, String>)
    fun get(key: String): String?
}
