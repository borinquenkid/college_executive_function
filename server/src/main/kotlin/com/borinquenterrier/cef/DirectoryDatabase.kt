package com.borinquenterrier.cef

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Server-wide index of known students/staff, keyed by the studentId LTI launches derive
 * (see LtiLaunchHandler). Deliberately a single file at `<tenantBaseDir>/_directory.db`, a
 * sibling of `.session-secret` — this is server-level metadata, not tenant data, so it lives
 * outside the hash-partitioned tenant tree TenantDatabaseFactory builds (see docs/adr/0002).
 *
 * Nothing before this needed to enumerate "which studentIds exist" — every other lookup already
 * has the studentId in hand (from a signed cookie). The staff console is the first thing that
 * needs a list, hence this being introduced alongside LTI auth rather than earlier.
 */
class DirectoryDatabase(tenantBaseDir: String) {

    private val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFile(tenantBaseDir).absolutePath}")

    init {
        try {
            dbFile(tenantBaseDir).parentFile?.mkdirs()
            driver.execute(
                null,
                """
                CREATE TABLE IF NOT EXISTS students (
                  student_id TEXT PRIMARY KEY NOT NULL,
                  created_at INTEGER NOT NULL,
                  is_staff INTEGER NOT NULL DEFAULT 0,
                  session_epoch INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
                0, null
            )
        } catch (e: Exception) {
            // The driver is already open at this point (property initializer runs before this
            // init block) — without this, a failed CREATE TABLE leaks the connection since the
            // object never finishes constructing and nothing else holds a reference to close it.
            driver.close()
            throw e
        }
    }

    /** Idempotent — called on every LTI launch. Only writes on first sight of a studentId, so a
     *  student's a thousandth launch costs the same as their first (a plain UPDATE would too, but
     *  there's nothing to update: created_at/is_staff don't change after the first launch — a
     *  role change on a later launch is intentionally out of scope for this table, see ADR 0007).
     *  Returns the tenant's current session_epoch so the caller can embed it in the freshly-minted
     *  UserSession — see resolveStudentId()'s epoch check in Application.kt. */
    fun recordLaunch(studentId: String, isStaff: Boolean, nowMillis: Long): Int {
        driver.execute(
            null,
            "INSERT OR IGNORE INTO students (student_id, created_at, is_staff, session_epoch) VALUES (?, ?, ?, 0)",
            3
        ) {
            bindString(0, studentId)
            bindLong(1, nowMillis)
            bindLong(2, if (isStaff) 1L else 0L)
        }
        return currentEpoch(studentId)
    }

    fun currentEpoch(studentId: String): Int =
        driver.executeQuery(
            null, "SELECT session_epoch FROM students WHERE student_id = ?",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0)?.toInt() ?: 0 else 0) },
            1
        ) { bindString(0, studentId) }.value

    /** Bumps and returns the new epoch. resolveStudentId() rejects any session minted with an
     *  older epoch on its very next request — this is the staff console's "force-reset a
     *  student's session" mechanism (see WebStaffHandler.handleResetSession). */
    fun bumpSessionEpoch(studentId: String): Int {
        driver.execute(
            null, "UPDATE students SET session_epoch = session_epoch + 1 WHERE student_id = ?", 1
        ) { bindString(0, studentId) }
        return currentEpoch(studentId)
    }

    data class StudentRecord(val studentId: String, val createdAt: Long, val isStaff: Boolean)

    fun listStudents(): List<StudentRecord> =
        driver.executeQuery(
            null,
            "SELECT student_id, created_at, is_staff FROM students ORDER BY created_at DESC",
            { cursor ->
                val result = mutableListOf<StudentRecord>()
                while (cursor.next().value) {
                    result += StudentRecord(
                        studentId = cursor.getString(0)!!,
                        createdAt = cursor.getLong(1)!!,
                        isStaff = cursor.getLong(2) == 1L
                    )
                }
                QueryResult.Value(result)
            },
            0
        ).value

    private fun dbFile(tenantBaseDir: String) = File(tenantBaseDir, "_directory.db")
}
