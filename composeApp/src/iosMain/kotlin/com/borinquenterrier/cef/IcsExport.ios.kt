package com.borinquenterrier.cef

import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIWindow

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
    
    // Try to find the active view controller to present the share sheet
    var rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (rootViewController == null) {
        val windows = UIApplication.sharedApplication.windows
        for (i in 0 until windows.size.toInt()) {
            val window = windows[i] as? UIWindow
            if (window?.rootViewController != null) {
                rootViewController = window.rootViewController
                break
            }
        }
    }
    
    rootViewController?.presentViewController(activityController, true, null)
    
    return tempPath
}
