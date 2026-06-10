package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

/**
 * Returns a Settings instance backed by the student's own SQLite database file.
 * Each studentId gets a physically isolated store — no cross-tenant key access is possible.
 */
class TenantSettingsFactory(private val cache: TenantConnectionCache) {
    suspend fun settingsFor(studentId: String): Settings =
        SqliteSettings(cache.getOrOpen(studentId))
}
