package com.borinquenterrier.cef

import android.content.Intent
import android.net.Uri

actual object PlatformUtils {
    actual fun openBrowser(url: String) {
        val context = AndroidAppContext.applicationContext ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
