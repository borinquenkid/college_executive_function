package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

/**
 * Runs VacuumBackupRunner on a fixed interval for as long as the process is alive — the
 * "out of the box" backup mechanism so IT staff don't have to configure host cron themselves.
 * Enabled only when CEF_BACKUP_DIR is set (see main()). Logs each run's result to stdout
 * (visible via `docker logs`) and keeps looping even if one run fails.
 */
class ScheduledBackupJob(
    private val tenantBaseDir: String,
    private val backupDir: String,
    private val intervalHours: Long
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                runOnce()
                delay(intervalHours.hours)
            }
        }
    }

    /** Runs a single backup pass. Exposed separately from [start] so it's testable without
     *  driving an infinite loop. */
    suspend fun runOnce() {
        try {
            val results = VacuumBackupRunner(backupDir).runAll(tenantBaseDir)
            val failures = results.count { !it.success }
            println("[ScheduledBackupJob] Backed up ${results.size} tenant database(s) to $backupDir ($failures failure(s))")
        } catch (e: Exception) {
            println("[ScheduledBackupJob] Backup run failed: ${e.message}")
        }
    }
}
