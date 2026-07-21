package com.borinquenterrier.cef

import io.ktor.client.plugins.cookies.HttpCookies
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

// ── HTTP route: session-based tenant routing ──────────────────────────────────
// X-Student-ID used to be a trusted client-supplied header (see AuthSessionIntegrationTest for
// the full session-model coverage); these tests specifically pin down that the header is now
// inert — knowing/spoofing it grants no access on its own, only a valid session cookie does.

class StudentIdRoutingTest {

    @Test
    fun `GET settings uses default container when X-Student-ID header is absent`() = testApplication {
        val container = buildTestContainer()
        container.settings.putString("CEF_GEMINI_API_KEY", "default-key")

        application { module(testContainer = container) }

        val response = client.get("/api/settings")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("default-key"), "the stored API key must never be sent to the client")
        assertTrue(body.contains("\"hasApiKey\":true"))
    }

    @Test
    fun `spoofing X-Student-ID with a stranger's studentId does not switch tenants`() = runBlocking {
        val baseDir = Files.createTempDirectory("cef-route-isolation-test").toFile()
        try {
            val factory = ServerContainerFactory(tenantBaseDir = baseDir.absolutePath)
            factory.containerFor("bob").settings.putString("CEF_GEMINI_API_KEY", "bob-key")

            testApplication {
                application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }
                val client = createClient { install(HttpCookies) }
                client.post("/api/auth/start")

                // Claiming to be "bob" via the old header no longer has any effect — the session
                // cookie alone decides the tenant, so this must NOT see bob's settings.
                val response = client.get("/api/settings") { header("X-Student-ID", "bob") }
                assertEquals(HttpStatusCode.OK, response.status)
                assertFalse(response.bodyAsText().contains("\"hasApiKey\":true"), "spoofed header must not grant access to bob's tenant")
            }
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun `X-Student-ID header alone, without a session, is not sufficient to route anywhere`() = runBlocking {
        val baseDir = Files.createTempDirectory("cef-route-default-test").toFile()
        try {
            val factory = ServerContainerFactory(tenantBaseDir = baseDir.absolutePath)
            factory.containerFor("default").settings.putString("CEF_GEMINI_API_KEY", "default-key")

            testApplication {
                application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }

                val response = client.get("/api/settings") { header("X-Student-ID", "default") }
                assertEquals(HttpStatusCode.Unauthorized, response.status, "no session cookie means no access, regardless of the header")
            }
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun `X-Student-ID header with path-traversal characters has no effect once a session exists`() = runBlocking {
        val baseDir = Files.createTempDirectory("cef-route-traversal-test").toFile()
        try {
            val factory = ServerContainerFactory(tenantBaseDir = baseDir.absolutePath)

            testApplication {
                application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }
                val client = createClient { install(HttpCookies) }
                client.post("/api/auth/start")

                val response = client.get("/api/settings") { header("X-Student-ID", "../../../etc/passwd") }
                assertEquals(HttpStatusCode.OK, response.status, "the header is ignored entirely, so a malicious value can't even trigger a 400 anymore")
            }
        } finally {
            baseDir.deleteRecursively()
        }
    }
}

// ── helpers ───────────────────────────────────────────────────────────────────

private fun buildTestContainer(): com.borinquenterrier.cef.DependencyContainer {
    val tmpDir = Files.createTempDirectory("cef-route-test").toFile()
    val factory = ServerContainerFactory(tmpDir.absolutePath)
    return runBlocking { factory.containerFor("default") }
}
