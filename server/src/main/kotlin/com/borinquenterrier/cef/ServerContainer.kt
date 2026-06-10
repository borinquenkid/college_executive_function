package com.borinquenterrier.cef

import java.io.File
import kotlinx.coroutines.runBlocking

object ServerContainer {
    private val factory by lazy {
        val tenantBaseDir = File(System.getProperty("user.home"), ".cef/tenants").absolutePath
        ServerContainerFactory(tenantBaseDir = tenantBaseDir)
    }

    val container: DependencyContainer by lazy {
        runBlocking { factory.containerFor("default") }
    }

    suspend fun containerFor(studentId: String): DependencyContainer =
        factory.containerFor(studentId)
}
