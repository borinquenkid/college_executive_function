package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/** Coarse account metadata only — deliberately no calendar/source/chat content, per
 *  docs/adr/0007-staff-console-via-lti-roles.md. lastActiveMillis is null if the tenant's db files
 *  can't be stat'd (e.g. deleted out from under the directory row). */
@Serializable
data class StudentSummary(
    val studentId: String,
    val createdAtMillis: Long,
    val lastActiveMillis: Long?
)

object WebStaffHandler {
    suspend fun handleListStudents(
        call: ApplicationCall,
        directoryDatabase: DirectoryDatabase,
        dbFactory: TenantDatabaseFactory
    ) {
        val summaries = directoryDatabase.listStudents()
            .filterNot { it.isStaff } // staff accounts aren't "students" to list here
            .map { record ->
                StudentSummary(
                    studentId = record.studentId,
                    createdAtMillis = record.createdAt,
                    lastActiveMillis = lastActiveMillis(dbFactory, record.studentId)
                )
            }
        call.respond(summaries)
    }

    suspend fun handleResetSession(
        call: ApplicationCall,
        directoryDatabase: DirectoryDatabase,
        studentId: String
    ) {
        directoryDatabase.bumpSessionEpoch(studentId)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    /** Tenant DBs run in WAL mode (see TenantDatabaseFactory.openDriver) — recent writes land in
     *  the .db-wal file until a checkpoint, so the main .db file's mtime alone reads stale for a
     *  busy tenant. Take the newest of .db/.db-wal/.db-shm instead. */
    private fun lastActiveMillis(dbFactory: TenantDatabaseFactory, studentId: String): Long? {
        val dbFile = dbFactory.dbFileFor(studentId)
        val candidates = listOf(dbFile, java.io.File(dbFile.path + "-wal"), java.io.File(dbFile.path + "-shm"))
        return candidates.filter { it.exists() }.maxOfOrNull { it.lastModified() }
    }
}
