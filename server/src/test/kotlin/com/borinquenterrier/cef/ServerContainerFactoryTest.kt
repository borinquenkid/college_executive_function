package com.borinquenterrier.cef

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class ServerContainerFactoryTest {

    private lateinit var baseDir: File
    private lateinit var factory: ServerContainerFactory

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("cef-server-container-test").toFile()
        factory = ServerContainerFactory(tenantBaseDir = baseDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { factory.closeAll() }
        baseDir.deleteRecursively()
    }

    // ── settings provider ──────────────────────────────────────────────────────

    @Test
    fun `containerFor returns a DependencyContainer whose settings is backed by SqliteSettings`() = runBlocking {
        val container = factory.containerFor("alice")
        assertIs<SqliteSettings>(container.settings)
    }

    @Test
    fun `containerFor does not use PreferencesSettings`() = runBlocking {
        val container = factory.containerFor("alice")
        assertFalse(
            container.settings is com.russhwolf.settings.PreferencesSettings,
            "settings must not be the global JVM PreferencesSettings"
        )
    }

    // ── isolation ─────────────────────────────────────────────────────────────

    @Test
    fun `settings written for student A are not visible to student B`() = runBlocking {
        val alice = factory.containerFor("alice")
        val bob = factory.containerFor("bob")

        alice.settings.putString("GOOGLE_ACCESS_TOKEN", "alice-token")

        assertNull(bob.settings.getStringOrNull("GOOGLE_ACCESS_TOKEN"))
    }

    @Test
    fun `two students can store different values under the same key`() = runBlocking {
        val alice = factory.containerFor("alice")
        val bob = factory.containerFor("bob")

        alice.settings.putString("CEF_GEMINI_API_KEY", "alice-key")
        bob.settings.putString("CEF_GEMINI_API_KEY", "bob-key")

        assertEquals("alice-key", alice.settings.getString("CEF_GEMINI_API_KEY", ""))
        assertEquals("bob-key", bob.settings.getString("CEF_GEMINI_API_KEY", ""))
    }

    @Test
    fun `clearing settings for one student does not affect another`() = runBlocking {
        val alice = factory.containerFor("alice")
        val bob = factory.containerFor("bob")

        alice.settings.putString("key", "a")
        bob.settings.putString("key", "b")

        alice.settings.clear()

        assertNull(alice.settings.getStringOrNull("key"))
        assertEquals("b", bob.settings.getString("key", ""))
    }

    // ── caching ───────────────────────────────────────────────────────────────

    @Test
    fun `containerFor returns the same instance on repeated calls for the same studentId`() = runBlocking {
        val first = factory.containerFor("alice")
        val second = factory.containerFor("alice")
        assertSame(first, second)
    }

    @Test
    fun `containerFor returns distinct instances for different studentIds`() = runBlocking {
        val alice = factory.containerFor("alice")
        val bob = factory.containerFor("bob")
        assertNotSame(alice, bob)
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    fun `settings persist across multiple containerFor calls for the same student`() = runBlocking {
        factory.containerFor("alice").settings.putString("token", "persisted")

        val readBack = factory.containerFor("alice").settings.getString("token", "")
        assertEquals("persisted", readBack)
    }

    // ── settingsFactory exposure ───────────────────────────────────────────────

    @Test
    fun `settingsFactory is a TenantSettingsFactory`() {
        assertIs<TenantSettingsFactory>(factory.settingsFactory)
    }
}

// ── HTTP route: X-Student-ID header routing ───────────────────────────────────

class StudentIdRoutingTest {

    @Test
    fun `GET settings uses default container when X-Student-ID header is absent`() = testApplication {
        val container = buildTestContainer()
        container.settings.putString("CEF_GEMINI_API_KEY", "default-key")

        application { module(testContainer = container) }

        val response = client.get("/api/settings")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("default-key"))
    }
}

// ── helpers ───────────────────────────────────────────────────────────────────

private fun buildTestContainer(): com.borinquenterrier.cef.DependencyContainer {
    val tmpDir = Files.createTempDirectory("cef-route-test").toFile()
    val factory = ServerContainerFactory(tmpDir.absolutePath)
    return runBlocking { factory.containerFor("default") }
}
