package com.borinquenterrier.cef

import java.awt.Desktop
import java.net.URI

actual object PlatformUtils {
    actual fun openBrowser(url: String) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
