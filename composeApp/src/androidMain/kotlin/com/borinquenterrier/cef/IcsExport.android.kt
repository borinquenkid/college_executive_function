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
    val context =
        AndroidAppContext.applicationContext ?: throw Exception("Android context not initialized")

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
    }

    // Fallback for older versions
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
    }
    val file = File(downloadsDir, "academic_calendar.ics")
    file.writeText(content)
    return file.absolutePath
}
