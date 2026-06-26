package com.borinquenterrier.cef

import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

internal fun findKeyWindow(): UIWindow? =
    UIApplication.sharedApplication.connectedScenes
        .mapNotNull { it as? UIWindowScene }
        .flatMap { it.windows.mapNotNull { w -> w as? UIWindow } }
        .firstOrNull { it.isKeyWindow() }
        ?: UIApplication.sharedApplication.keyWindow
