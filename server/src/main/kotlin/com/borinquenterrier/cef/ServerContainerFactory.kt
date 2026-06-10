package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.DriverFactory
import java.io.File

class ServerContainerFactory(
    private val tenantBaseDir: String,
    private val modelBasePath: String = File(System.getProperty("user.home"), ".cef/models").absolutePath
) {
    private val dbFactory = TenantDatabaseFactory(tenantBaseDir)
    private val connectionCache = TenantConnectionCache(
        capacity = 1000,
        baseDir = tenantBaseDir,
        driverFactory = { path -> dbFactory.openDriver(File(path).nameWithoutExtension) }
    )
    val settingsFactory = TenantSettingsFactory(connectionCache)

    private val lock = Any()
    private val containerCache = linkedMapOf<String, DependencyContainer>()

    suspend fun containerFor(studentId: String): DependencyContainer {
        synchronized(lock) { containerCache[studentId]?.let { return it } }

        val settings = settingsFactory.settingsFor(studentId)
        val container = DependencyContainer(
            settings = settings,
            logger = Logger(settings),
            driverFactory = TenantDriverFactory(studentId, dbFactory),
            modelBasePath = modelBasePath,
            fileReader = LocalFileReader(),
            docxReader = DocxReader(),
            pdfReader = PdfReader()
        )

        synchronized(lock) { containerCache[studentId] = container }
        return container
    }

    suspend fun closeAll() = connectionCache.closeAll()
}
