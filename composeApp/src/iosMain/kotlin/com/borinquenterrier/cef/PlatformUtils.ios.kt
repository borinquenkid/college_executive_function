package com.borinquenterrier.cef

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual object PlatformUtils {
    actual fun openBrowser(url: String) {
        val nsUrl = NSURL(string = url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
