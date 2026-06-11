package com.borinquenterrier.cef

import java.io.File

/**
 * Resolves the standard OS-compliant application data directory for the JVM target.
 * Parameterized to support clean unit testing on any OS.
 */
fun getAppDirectory(
    osName: String = System.getProperty("os.name") ?: "",
    userHome: String = System.getProperty("user.home") ?: "",
    envMap: Map<String, String> = System.getenv()
): File {
    val os = osName.lowercase()
    val appDirName = "CollegeExecutiveFunction"
    
    val baseDir = when {
        os.contains("mac") -> File(userHome, "Library/Application Support/$appDirName")
        os.contains("win") -> {
            val appData = envMap["APPDATA"]
            if (!appData.isNullOrBlank()) File(appData, appDirName) else File(userHome, appDirName)
        }
        else -> { // Linux/Unix
            val dataHome = envMap["XDG_DATA_HOME"]
            if (!dataHome.isNullOrBlank()) {
                File(dataHome, appDirName)
            } else {
                File(userHome, ".local/share/$appDirName")
            }
        }
    }
    
    return baseDir
}
