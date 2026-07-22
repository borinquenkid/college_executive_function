package com.borinquenterrier.cef

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

actual fun generateIcsString(events: List<Event>): String {
    // We can write a simple manual builder for Android/iOS or use a custom pure Kotlin builder
    return IcsStringBuilder.buildIcsString(events)
}

actual fun writeIcsFile(content: String): String {
    val context = AndroidAppContext.applicationContext
        ?: return "Export failed: Android context not initialized"

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "academic_calendar.ics")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/calendar")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                return "Saved to Downloads/academic_calendar.ics"
            }
            return "Export failed: could not create a Downloads entry"
        }

        // Fallback for API 26-28 (pre-scoped-storage): needs the WRITE_EXTERNAL_STORAGE
        // permission declared in the manifest AND granted at runtime (not requested here —
        // this path degrades gracefully to a clear failure message on devices where it
        // isn't granted, rather than crashing the whole export flow).
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, "academic_calendar.ics")
        file.writeText(content)
        file.absolutePath
    } catch (e: Exception) {
        println("[IcsExport] Failed to write ICS file: ${e.message}")
        "Export failed: ${e.message}"
    }
}
