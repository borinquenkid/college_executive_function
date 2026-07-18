package com.borinquenterrier.cef

import android.app.Application

class CefApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Earliest possible hook — before MainActivity, before Compose, before DI — so a
        // crash during startup itself still has a chance to be traced.
        installGlobalCrashHandler()
    }
}
