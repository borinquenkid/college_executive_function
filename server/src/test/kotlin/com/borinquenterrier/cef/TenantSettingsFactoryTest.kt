package com.borinquenterrier.cef

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class TenantSettingsFactoryTest {

    private lateinit var baseDir: File
    private lateinit var cache: TenantConnectionCache
    private lateinit var factory: TenantSettingsFactory

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("cef-tenant-settings-test").toFile()
        val dbFactory = TenantDatabaseFactory(baseDir.absolutePath)
        cache = TenantConnectionCache(
            capacity = 10,
            baseDir = baseDir.absolutePath,
            driverFactory = { path -> dbFactory.openDriver(File(path).nameWithoutExtension) }
        )
        factory = TenantSettingsFactory(cache)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { cache.closeAll() }
        baseDir.deleteRecursively()
    }

    // ── basic behaviour ───────────────────────────────────────────────────────

    @Test
    fun `settingsFor returns a Settings instance`() = runBlocking {
        val settings = factory.settingsFor("alice")
        assertNotNull(settings)
    }

    @Test
    fun `settingsFor supports read and write`() = runBlocking {
        val settings = factory.settingsFor("alice")
        settings.putString("key", "value")
        assertEquals("value", settings.getString("key", ""))
    }

    // ── isolation ─────────────────────────────────────────────────────────────

    @Test
    fun `settings written for student A are not visible to student B`() = runBlocking {
        val aliceSettings = factory.settingsFor("alice")
        val bobSettings = factory.settingsFor("bob")

        aliceSettings.putString("GOOGLE_ACCESS_TOKEN", "alice-token")

        assertNull(bobSettings.getStringOrNull("GOOGLE_ACCESS_TOKEN"))
    }

    @Test
    fun `student B can store a different value under the same key`() = runBlocking {
        val aliceSettings = factory.settingsFor("alice")
        val bobSettings = factory.settingsFor("bob")

        aliceSettings.putString("GOOGLE_REFRESH_TOKEN", "alice-refresh")
        bobSettings.putString("GOOGLE_REFRESH_TOKEN", "bob-refresh")

        assertEquals("alice-refresh", aliceSettings.getString("GOOGLE_REFRESH_TOKEN", ""))
        assertEquals("bob-refresh", bobSettings.getString("GOOGLE_REFRESH_TOKEN", ""))
    }

    @Test
    fun `clearing settings for one student does not affect another`() = runBlocking {
        val aliceSettings = factory.settingsFor("alice")
        val bobSettings = factory.settingsFor("bob")

        aliceSettings.putString("key", "alice-value")
        bobSettings.putString("key", "bob-value")

        aliceSettings.clear()

        assertNull(aliceSettings.getStringOrNull("key"))
        assertEquals("bob-value", bobSettings.getString("key", ""))
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    fun `settings persist across repeated calls to settingsFor`() = runBlocking {
        factory.settingsFor("alice").putString("token", "persisted-value")

        val readBack = factory.settingsFor("alice").getString("token", "")
        assertEquals("persisted-value", readBack)
    }

    // ── GoogleTokenRepository integration ─────────────────────────────────────

    @Test
    fun `GoogleTokenRepository backed by tenant settings is isolated per student`() = runBlocking {
        val aliceRepo = GoogleTokenRepository(factory.settingsFor("alice"))
        val bobRepo = GoogleTokenRepository(factory.settingsFor("bob"))

        aliceRepo.saveTokens("alice-access", "alice-refresh")

        assertFalse(bobRepo.hasTokens())
        assertEquals("alice-access", aliceRepo.getAccessToken())
    }

    @Test
    fun `clearing tokens for one student does not affect another`() = runBlocking {
        val aliceRepo = GoogleTokenRepository(factory.settingsFor("alice"))
        val bobRepo = GoogleTokenRepository(factory.settingsFor("bob"))

        aliceRepo.saveTokens("alice-access", "alice-refresh")
        bobRepo.saveTokens("bob-access", "bob-refresh")

        aliceRepo.clearTokens()

        assertFalse(aliceRepo.hasTokens())
        assertTrue(bobRepo.hasTokens())
        assertEquals("bob-access", bobRepo.getAccessToken())
    }
}
