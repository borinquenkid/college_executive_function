package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import com.russhwolf.settings.MapSettings

class GoogleTokenRepositoryTest : FunSpec({

    test("Tokens are saved and retrieved correctly") {
        val settings = MapSettings()
        val repository = GoogleTokenRepository(settings)
        
        repository.saveTokens("access-123", "refresh-456")
        
        repository.getAccessToken() shouldBe "access-123"
        repository.getRefreshToken() shouldBe "refresh-456"
        repository.hasTokens() shouldBe true
    }

    test("hasTokens returns false when no tokens exist") {
        val settings = MapSettings()
        val repository = GoogleTokenRepository(settings)
        
        repository.hasTokens() shouldBe false
    }
})
