package com.borinquenterrier.cef

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.russhwolf.settings.Settings

/**
 * Settings implementation backed by a _settings key-value table in a SqlDriver.
 * All types are stored as TEXT; numeric/boolean types are serialised via toString()
 * and parsed back on read. Safe to instantiate multiple times over the same driver.
 */
class SqliteSettings(private val driver: SqlDriver) : Settings {

    init {
        driver.execute(
            null,
            "CREATE TABLE IF NOT EXISTS _settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
            0, null
        )
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    private fun get(key: String): String? =
        driver.executeQuery(
            null, "SELECT value FROM _settings WHERE key = ?",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            1
        ) { bindString(0, key) }.value

    private fun put(key: String, value: String) {
        driver.execute(null, "INSERT OR REPLACE INTO _settings (key, value) VALUES (?, ?)", 2) {
            bindString(0, key)
            bindString(1, value)
        }
    }

    // ── Settings interface ────────────────────────────────────────────────────

    override val keys: Set<String>
        get() = driver.executeQuery(
            null, "SELECT key FROM _settings", { cursor ->
                val result = mutableSetOf<String>()
                while (cursor.next().value) cursor.getString(0)?.let { result += it }
                QueryResult.Value(result)
            }, 0
        ).value

    override val size: Int
        get() = driver.executeQuery(
            null, "SELECT COUNT(*) FROM _settings",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0)?.toInt() ?: 0 else 0) },
            0
        ).value

    override fun clear() { driver.execute(null, "DELETE FROM _settings", 0, null) }
    override fun remove(key: String) {
        driver.execute(null, "DELETE FROM _settings WHERE key = ?", 1) { bindString(0, key) }
    }
    override fun hasKey(key: String): Boolean = get(key) != null

    override fun putString(key: String, value: String) = put(key, value)
    override fun getString(key: String, defaultValue: String): String = get(key) ?: defaultValue
    override fun getStringOrNull(key: String): String? = get(key)

    override fun putInt(key: String, value: Int) = put(key, value.toString())
    override fun getInt(key: String, defaultValue: Int): Int = get(key)?.toIntOrNull() ?: defaultValue
    override fun getIntOrNull(key: String): Int? = get(key)?.toIntOrNull()

    override fun putLong(key: String, value: Long) = put(key, value.toString())
    override fun getLong(key: String, defaultValue: Long): Long = get(key)?.toLongOrNull() ?: defaultValue
    override fun getLongOrNull(key: String): Long? = get(key)?.toLongOrNull()

    override fun putFloat(key: String, value: Float) = put(key, value.toString())
    override fun getFloat(key: String, defaultValue: Float): Float = get(key)?.toFloatOrNull() ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = get(key)?.toFloatOrNull()

    override fun putDouble(key: String, value: Double) = put(key, value.toString())
    override fun getDouble(key: String, defaultValue: Double): Double = get(key)?.toDoubleOrNull() ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = get(key)?.toDoubleOrNull()

    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = get(key)?.toBooleanStrictOrNull() ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = get(key)?.toBooleanStrictOrNull()
}
