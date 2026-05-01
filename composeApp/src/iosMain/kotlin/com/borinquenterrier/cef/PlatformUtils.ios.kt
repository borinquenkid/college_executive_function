package com.borinquenterrier.cef

actual object PlatformUtils {
    actual fun openBrowser(url: String) {
        // Implementation would use UIApplication.sharedApplication.openURL(NSURL(string = url)!!)
    }
}
