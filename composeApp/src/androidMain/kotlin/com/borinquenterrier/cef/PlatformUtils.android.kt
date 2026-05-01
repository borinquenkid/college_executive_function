package com.borinquenterrier.cef

import android.content.Intent
import android.net.Uri

actual object PlatformUtils {
    // Note: In a real Android app, we'd need a context, 
    // but for this CLI-driven prototype we'll assume it's handled via the UI layer.
    // For now, this is a placeholder to satisfy the 'actual' requirement.
    actual fun openBrowser(url: String) {
        // Implementation would use Context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
