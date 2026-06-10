package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LRU cache mapping studentId → SqlDriver (one SQLite file per student).
 *
 * DB path structure: baseDir/xx/yy/studentId.db  (xx/yy = first 4 hex chars of MD5 hash)
 * Evicts and closes the least-recently-used entry when capacity is exceeded.
 * All mutations are guarded by a coroutine Mutex for safe concurrent access.
 */
class TenantConnectionCache(
    private val capacity: Int = 1000,
    val baseDir: String,
    private val driverFactory: (path: String) -> SqlDriver
) {
    private val mutex = Mutex()
    // accessOrder=true: get() moves entry to tail; head is the LRU candidate
    private val cache = LinkedHashMap<String, SqlDriver>(16, 0.75f, true)

    suspend fun getOrOpen(studentId: String): SqlDriver = mutex.withLock {
        cache[studentId]?.let { return@withLock it }

        val driver = driverFactory(dbPathFor(studentId))
        cache[studentId] = driver

        if (cache.size > capacity) {
            val iterator = cache.iterator()
            val eldest = iterator.next()
            iterator.remove()
            eldest.value.close()
        }

        driver
    }

    suspend fun close(studentId: String): Unit = mutex.withLock {
        cache.remove(studentId)?.close()
    }

    suspend fun closeAll(): Unit = mutex.withLock {
        cache.values.forEach { it.close() }
        cache.clear()
    }

    fun dbPathFor(studentId: String): String {
        val hash = md5(studentId)
        return "$baseDir/${hash.substring(0, 2)}/${hash.substring(2, 4)}/$studentId.db"
    }

    private fun md5(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
