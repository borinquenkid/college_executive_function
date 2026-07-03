package com.borinquenterrier.cef

import java.io.File

object ServerContainer {
    private val factory by lazy {
        // Defaults to a path under the operator's home dir for bare local runs; Docker
        // deployments set this to the mounted tenant-data volume (see docs/adr/0002).
        val tenantBaseDir = System.getenv("CEF_TENANT_BASE_DIR")
            ?: File(System.getProperty("user.home"), ".cef/tenants").absolutePath
        ServerContainerFactory(tenantBaseDir = tenantBaseDir)
    }

    suspend fun containerFor(studentId: String): DependencyContainer =
        factory.containerFor(studentId)
}
