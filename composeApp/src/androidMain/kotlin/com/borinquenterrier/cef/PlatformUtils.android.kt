package com.borinquenterrier.cef

import android.content.Intent
import androidx.core.net.toUri

actual object PlatformUtils {
    actual fun openBrowser(url: String) {
        val context = AndroidAppContext.applicationContext ?: return
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
