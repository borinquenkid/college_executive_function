package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences

class GetMyTokenDebug : FunSpec({

    test("Print current tokens from local settings") {
        // Specifically target the JVM Preferences storage
        val prefs = Preferences.userRoot().node("com.borinquenterrier.college_executive_function")
        val settings = PreferencesSettings(prefs)
        val tokenRepo = GoogleTokenRepository(settings)
        
        println("==========================================")
        println("YOUR GOOGLE ACCESS TOKEN:")
        println(tokenRepo.getAccessToken())
        println("------------------------------------------")
        println("YOUR GOOGLE REFRESH TOKEN:")
        println(tokenRepo.getRefreshToken())
        println("==========================================")
    }
})
