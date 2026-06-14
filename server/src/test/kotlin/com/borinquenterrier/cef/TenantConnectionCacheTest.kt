package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import io.mockk.*
import kotlin.test.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class TenantConnectionCacheTest {

    private val createdDrivers = mutableListOf<SqlDriver>()
    private lateinit var cache: TenantConnectionCache

    @BeforeTest
    fun setUp() {
        createdDrivers.clear()
        cache = makeCache(capacity = 3)
    }

    private fun makeCache(capacity: Int = 3, baseDir: String = "/tmp/cef-test") =
        TenantConnectionCache(
            capacity = capacity,
            baseDir = baseDir,
            driverFactory = { mockk<SqlDriver>(relaxed = true).also { createdDrivers.add(it) } }
        )

    // ── getOrOpen ────────────────────────────────────────────────────────────

    @Test
    fun `getOrOpen returns a driver for a new studentId`() = runBlocking {
        assertNotNull(cache.getOrOpen("student-1"))
    }

    @Test
    fun `getOrOpen returns the same driver on repeated calls`() = runBlocking {
        val first = cache.getOrOpen("student-1")
        val second = cache.getOrOpen("student-1")
        assertSame(first, second)
        assertEquals(1, createdDrivers.size)
    }

    @Test
    fun `getOrOpen creates a new driver when a closed entry is re-requested`() = runBlocking {
        val original = cache.getOrOpen("student-1")
        cache.close("student-1")
        val replacement = cache.getOrOpen("student-1")
        assertNotSame(original, replacement)
        assertEquals(2, createdDrivers.size)
    }

    // ── eviction ─────────────────────────────────────────────────────────────

    @Test
    fun `evicts and closes the LRU entry when capacity is exceeded`() = runBlocking {
        val d1 = cache.getOrOpen("student-1")
        cache.getOrOpen("student-2")
        cache.getOrOpen("student-3")

        cache.getOrOpen("student-4") // should evict student-1

        verify(exactly = 1) { d1.close() }
    }

    @Test
    fun `accessing an entry promotes it so it is not the LRU eviction candidate`() = runBlocking {
        cache.getOrOpen("student-1")
        val d2 = cache.getOrOpen("student-2")
        cache.getOrOpen("student-3")

        cache.getOrOpen("student-1") // promote student-1; student-2 is now LRU

        cache.getOrOpen("student-4") // should evict student-2

        verify(exactly = 1) { d2.close() }
        verify(exactly = 0) { createdDrivers[0].close() } // student-1 not evicted
    }

    @Test
    fun `evicted entries are closed exactly once`() = runBlocking {
        val d1 = cache.getOrOpen("student-1")
        cache.getOrOpen("student-2")
        cache.getOrOpen("student-3")
        cache.getOrOpen("student-4") // evicts student-1

        verify(exactly = 1) { d1.close() }
    }

    // ── close ────────────────────────────────────────────────────────────────

    @Test
    fun `close removes the entry and closes its driver`() = runBlocking {
        val driver = cache.getOrOpen("student-1")
        cache.close("student-1")
        verify(exactly = 1) { driver.close() }
    }

    @Test
    fun `close on unknown studentId is a no-op`() = runBlocking {
        cache.close("nonexistent") // must not throw
    }

    // ── closeAll ─────────────────────────────────────────────────────────────

    @Test
    fun `closeAll closes all open drivers`() = runBlocking {
        val d1 = cache.getOrOpen("student-1")
        val d2 = cache.getOrOpen("student-2")
        cache.closeAll()
        verify(exactly = 1) { d1.close() }
        verify(exactly = 1) { d2.close() }
    }

    @Test
    fun `closeAll clears the cache so next getOrOpen creates a fresh driver`() = runBlocking {
        cache.getOrOpen("student-1")
        cache.closeAll()
        cache.getOrOpen("student-1")
        assertEquals(2, createdDrivers.size)
    }

    // ── dbPathFor ────────────────────────────────────────────────────────────

    @Test
    fun `dbPathFor ends with the studentId and db extension`() {
        assertTrue(cache.dbPathFor("alice").endsWith("alice.db"))
    }

    @Test
    fun `dbPathFor uses a 2-level 2-char hash directory structure`() {
        val path = cache.dbPathFor("alice")
        // Expected: /tmp/cef-test/xx/yy/alice.db
        val segments = path.split("/")
        val filename = segments.last()
        val dir2 = segments[segments.size - 2]
        val dir1 = segments[segments.size - 3]
        assertEquals("alice.db", filename)
        assertEquals(2, dir1.length)
        assertEquals(2, dir2.length)
    }

    @Test
    fun `dbPathFor is deterministic for the same studentId`() {
        assertEquals(cache.dbPathFor("alice"), cache.dbPathFor("alice"))
    }

    @Test
    fun `dbPathFor produces different paths for different studentIds`() {
        assertNotEquals(cache.dbPathFor("alice"), cache.dbPathFor("bob"))
    }

    // ── concurrency ──────────────────────────────────────────────────────────

    @Test
    fun `concurrent getOrOpen for the same studentId returns the same driver`() = runBlocking {
        val results = coroutineScope {
            (1..50).map { async { cache.getOrOpen("student-1") } }.awaitAll()
        }
        assertEquals(1, results.distinct().size)
        assertEquals(1, createdDrivers.size)
    }

    @Test
    fun `concurrent getOrOpen for different studentIds each get their own driver`() = runBlocking {
        val ids = (1..3).map { "student-$it" }
        val results = coroutineScope {
            ids.map { id -> async { id to cache.getOrOpen(id) } }.awaitAll()
        }
        val drivers = results.map { it.second }
        assertEquals(3, drivers.distinct().size)
    }
}
