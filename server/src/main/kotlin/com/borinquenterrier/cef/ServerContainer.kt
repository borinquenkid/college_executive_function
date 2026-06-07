package com.borinquenterrier.cef

import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences
import com.borinquenterrier.cef.db.DriverFactory
import java.io.File

object ServerContainer {
    val container: DependencyContainer by lazy {
        val prefs = Preferences.userNodeForPackage(DependencyContainer::class.java)
        val settings = PreferencesSettings(prefs)
        
        // Force test profile if needed, or stick to local profile
        if (settings.getString("run_profile", "").isBlank()) {
            settings.putString("run_profile", "local")
        }
        
        val logger = Logger(settings)
        val driverFactory = DriverFactory()
        
        val userHome = System.getProperty("user.home")
        val modelBasePath = File(userHome, ".cef/models").apply { mkdirs() }.absolutePath
        
        val fileReader = LocalFileReader()
        val docxReader = DocxReader()
        val pdfReader = PdfReader()

        DependencyContainer(
            settings = settings,
            logger = logger,
            driverFactory = driverFactory,
            modelBasePath = modelBasePath,
            fileReader = fileReader,
            docxReader = docxReader,
            pdfReader = pdfReader
        )
    }
}
