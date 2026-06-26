package com.borinquenterrier.cef

import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController

actual fun generateIcsString(events: List<Event>): String {
    return IcsStringBuilder.buildIcsString(events)
}

actual fun writeIcsFile(content: String): String {
    val tempDir = NSTemporaryDirectory()
    val tempPath = tempDir + "academic_calendar.ics"

    // Write using Okio FileSystem
    val fileSystem = getFileSystem()
    fileSystem.write(tempPath.toPath(), mustCreate = false) {
        writeUtf8(content)
    }

    // Open iOS Share Sheet
    val fileUrl = NSURL.fileURLWithPath(tempPath)
    val activityController = UIActivityViewController(listOf(fileUrl), null)

    findKeyWindow()?.rootViewController?.presentViewController(activityController, true, null)

    return tempPath
}
