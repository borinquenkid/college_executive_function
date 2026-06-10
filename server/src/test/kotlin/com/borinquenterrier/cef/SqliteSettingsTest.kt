package com.borinquenterrier.cef

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.*

class SqliteSettingsTest {

    private lateinit var settings: SqliteSettings

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        settings = SqliteSettings(driver)
    }

    // ── String ────────────────────────────────────────────────────────────────

    @Test
    fun `getString returns default when key is absent`() {
        assertEquals("default", settings.getString("missing", "default"))
    }

    @Test
    fun `getStringOrNull returns null when key is absent`() {
        assertNull(settings.getStringOrNull("missing"))
    }

    @Test
    fun `putString then getString returns the stored value`() {
        settings.putString("token", "abc123")
        assertEquals("abc123", settings.getString("token", ""))
    }

    @Test
    fun `putString overwrites existing value`() {
        settings.putString("token", "first")
        settings.putString("token", "second")
        assertEquals("second", settings.getString("token", ""))
    }

    @Test
    fun `getStringOrNull returns value when key exists`() {
        settings.putString("token", "abc123")
        assertEquals("abc123", settings.getStringOrNull("token"))
    }

    // ── Int ───────────────────────────────────────────────────────────────────

    @Test
    fun `getInt returns default when key is absent`() {
        assertEquals(42, settings.getInt("missing", 42))
    }

    @Test
    fun `putInt then getInt returns stored value`() {
        settings.putInt("count", 99)
        assertEquals(99, settings.getInt("count", 0))
    }

    @Test
    fun `getIntOrNull returns null when key is absent`() {
        assertNull(settings.getIntOrNull("missing"))
    }

    // ── Long ──────────────────────────────────────────────────────────────────

    @Test
    fun `putLong then getLong returns stored value`() {
        settings.putLong("ts", Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, settings.getLong("ts", 0L))
    }

    @Test
    fun `getLongOrNull returns null when key is absent`() {
        assertNull(settings.getLongOrNull("missing"))
    }

    // ── Boolean ───────────────────────────────────────────────────────────────

    @Test
    fun `putBoolean true then getBoolean returns true`() {
        settings.putBoolean("flag", true)
        assertTrue(settings.getBoolean("flag", false))
    }

    @Test
    fun `putBoolean false then getBoolean returns false`() {
        settings.putBoolean("flag", false)
        assertFalse(settings.getBoolean("flag", true))
    }

    @Test
    fun `getBooleanOrNull returns null when key is absent`() {
        assertNull(settings.getBooleanOrNull("missing"))
    }

    // ── Float / Double ────────────────────────────────────────────────────────

    @Test
    fun `putFloat then getFloat returns stored value`() {
        settings.putFloat("ratio", 3.14f)
        assertEquals(3.14f, settings.getFloat("ratio", 0f))
    }

    @Test
    fun `putDouble then getDouble returns stored value`() {
        settings.putDouble("pi", Math.PI)
        assertEquals(Math.PI, settings.getDouble("pi", 0.0))
    }

    // ── keys / size / hasKey ──────────────────────────────────────────────────

    @Test
    fun `keys returns all stored keys`() {
        settings.putString("a", "1")
        settings.putString("b", "2")
        assertEquals(setOf("a", "b"), settings.keys)
    }

    @Test
    fun `size reflects number of stored entries`() {
        assertEquals(0, settings.size)
        settings.putString("x", "1")
        assertEquals(1, settings.size)
        settings.putString("y", "2")
        assertEquals(2, settings.size)
    }

    @Test
    fun `hasKey returns true for existing key`() {
        settings.putString("key", "value")
        assertTrue(settings.hasKey("key"))
    }

    @Test
    fun `hasKey returns false for absent key`() {
        assertFalse(settings.hasKey("missing"))
    }

    // ── remove / clear ────────────────────────────────────────────────────────

    @Test
    fun `remove deletes the key`() {
        settings.putString("token", "abc")
        settings.remove("token")
        assertNull(settings.getStringOrNull("token"))
    }

    @Test
    fun `remove on absent key is a no-op`() {
        settings.remove("nonexistent") // must not throw
    }

    @Test
    fun `clear removes all keys`() {
        settings.putString("a", "1")
        settings.putString("b", "2")
        settings.clear()
        assertEquals(0, settings.size)
        assertTrue(settings.keys.isEmpty())
    }
}
