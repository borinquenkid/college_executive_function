package com.borinquenterrier.cef

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Runs a VACUUM INTO snapshot backup of every tenant SQLite database.
 *
 * Usage:
 *   java -cp server-all.jar com.borinquenterrier.cef.BackupCliKt <tenantBaseDir> <backupDir>
 * or, with env vars set (matches ServerContainer's CEF_TENANT_BASE_DIR):
 *   CEF_TENANT_BASE_DIR=/data/tenants CEF_BACKUP_DIR=/data/backups \
 *     java -cp server-all.jar com.borinquenterrier.cef.BackupCliKt
 *
 * See DEPLOYMENT.md for how to schedule this (cron + docker exec).
 */
fun main(args: Array<String>) {
    val tenantBaseDir = args.getOrNull(0)
        ?: System.getenv("CEF_TENANT_BASE_DIR")
        ?: error("Missing tenant base dir: pass as arg 1 or set CEF_TENANT_BASE_DIR")
    val backupDir = args.getOrNull(1)
        ?: System.getenv("CEF_BACKUP_DIR")
        ?: error("Missing backup dir: pass as arg 2 or set CEF_BACKUP_DIR")

    val results = runBlocking { VacuumBackupRunner(backupDir).runAll(tenantBaseDir) }
    val failures = results.filter { !it.success }

    println("Vacuumed ${results.size} tenant database(s) into $backupDir (${failures.size} failure(s))")
    failures.forEach { println("  FAILED ${it.sourcePath}: ${it.error?.message}") }

    if (failures.isNotEmpty()) exitProcess(1)
}
