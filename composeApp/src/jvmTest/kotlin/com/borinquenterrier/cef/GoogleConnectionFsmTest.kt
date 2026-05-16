package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import com.russhwolf.settings.MapSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first

class GoogleConnectionFsmTest : FunSpec({

    test("FSM: should transition Unlinked -> Connecting -> Linked on success") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)
        
        coEvery { authService.login() } returns Pair("valid-token", "refresh-token")
        coEvery { driveService.validateConnection("valid-token") } returns true
        
        val fsm = GoogleAccountFlow(authService, tokenRepo)
        fsm.driveService = driveService
        
        fsm.state.value shouldBe GoogleConnectionState.Unlinked
        
        fsm.connect()
        
        fsm.state.value shouldBe GoogleConnectionState.Linked
        tokenRepo.getAccessToken() shouldBe "valid-token"
    }

    test("FSM: should transition to Error if Drive validation fails") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)
        
        coEvery { authService.login() } returns Pair("valid-token", "refresh-token")
        coEvery { driveService.validateConnection("valid-token") } returns false
        
        val fsm = GoogleAccountFlow(authService, tokenRepo)
        fsm.driveService = driveService
        
        fsm.connect()
        
        fsm.state.value.shouldBeInstanceOf<GoogleConnectionState.Error>()
        (fsm.state.value as GoogleConnectionState.Error).message shouldBe "Connected to Google, but Drive access failed. Please ensure you checked the permission box in the browser."
        tokenRepo.hasTokens() shouldBe false // Should clear tokens on partial failure
    }

    test("FSM: should transition to Error if login throws") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        val authService = mockk<GoogleAuthService>(relaxed = true)
        
        coEvery { authService.login() } throws Exception("Network Error")
        
        val fsm = GoogleAccountFlow(authService, tokenRepo)
        // driveService not needed for this path
        
        fsm.connect()
        
        fsm.state.value.shouldBeInstanceOf<GoogleConnectionState.Error>()
        (fsm.state.value as GoogleConnectionState.Error).message shouldBe "Network Error"
    }

    test("FSM: should transition Linked -> Error when background auth error is reported") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("old-token", "refresh")
        
        val fsm = GoogleAccountFlow(mockk(), tokenRepo)
        fsm.state.value shouldBe GoogleConnectionState.Linked
        
        fsm.reportAuthError("Session expired")
        
        fsm.state.value.shouldBeInstanceOf<GoogleConnectionState.Error>()
        (fsm.state.value as GoogleConnectionState.Error).message shouldBe "Session expired"
    }

    test("FSM: should transition to Unlinked on disconnect") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("token", "refresh")
        
        val fsm = GoogleAccountFlow(mockk(relaxed = true), tokenRepo)
        fsm.state.value shouldBe GoogleConnectionState.Linked
        
        fsm.disconnect()
        
        fsm.state.value shouldBe GoogleConnectionState.Unlinked
        tokenRepo.hasTokens() shouldBe false
    }
})
