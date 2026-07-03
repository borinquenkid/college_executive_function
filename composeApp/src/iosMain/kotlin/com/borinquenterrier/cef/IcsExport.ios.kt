package com.borinquenterrier.cef

import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.popoverPresentationController

actual fun generateIcsString(events: List<Event>): String {
    return IcsStringBuilder.buildIcsString(events)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun writeIcsFile(content: String): String {
    val tempDir = NSTemporaryDirectory()
    // Unique per call — a fixed filename would race if export is triggered twice in quick
    // succession (e.g. a rapid double-tap), and the caller (StudioPanel.kt) already wraps
    // this whole call in try/catch, so write failures surface as "Export failed: ..." rather
    // than needing to be swallowed here.
    val tempPath = tempDir + "academic_calendar_${NSUUID().UUIDString}.ics"

    val fileSystem = getFileSystem()
    fileSystem.write(tempPath.toPath(), mustCreate = false) {
        writeUtf8(content)
    }

    // Open iOS Share Sheet
    val fileUrl = NSURL.fileURLWithPath(tempPath)
    val activityController = UIActivityViewController(listOf(fileUrl), null)

    findKeyWindow()?.rootViewController?.let { rootViewController ->
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            // UIActivityViewController requires a popover anchor on iPad, or UIKit crashes at
            // presentation time ("...whose sourceView is nil"). The project targets iPad
            // (TARGETED_DEVICE_FAMILY = "1,2"), so this matters even though today's test
            // device is an iPhone.
            activityController.popoverPresentationController?.apply {
                sourceView = rootViewController.view
                sourceRect = rootViewController.view.bounds
            }
        }
        rootViewController.presentViewController(activityController, true, null)
    }

    return tempPath
}
