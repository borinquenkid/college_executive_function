package com.borinquenterrier.cef

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond

class GoogleConnectionFsmTest : FunSpec({

    test("FSM: should transition Unlinked -> Connecting -> Linked on success") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)

        coEvery { authService.login() } returns Pair("valid-token", "refresh-token")
        coEvery { driveService.validateConnection("valid-token") } returns true

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
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

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
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

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
        // driveService not needed for this path

        fsm.connect()

        fsm.state.value.shouldBeInstanceOf<GoogleConnectionState.Error>()
        (fsm.state.value as GoogleConnectionState.Error).message shouldBe "Network Error"
    }

    test("FSM: should transition Linked -> Error when background auth error is reported") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("old-token", "refresh")

        val fsm = GoogleAccountFlow(mockk(), tokenRepo, HttpClient(MockEngine { respond("OK") }))
        fsm.state.value shouldBe GoogleConnectionState.Linked

        fsm.reportAuthError("Session expired")

        fsm.state.value.shouldBeInstanceOf<GoogleConnectionState.Error>()
        (fsm.state.value as GoogleConnectionState.Error).message shouldBe "Session expired"
    }

    test("FSM: should transition to Unlinked on disconnect") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("token", "refresh")

        val fsm = GoogleAccountFlow(mockk(relaxed = true), tokenRepo, HttpClient(MockEngine { respond("OK") }))
        fsm.state.value shouldBe GoogleConnectionState.Linked

        fsm.disconnect()

        fsm.state.value shouldBe GoogleConnectionState.Unlinked
        tokenRepo.hasTokens() shouldBe false
    }

    test("Startup Connection: should remain Unlinked if no tokens present") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
        fsm.driveService = driveService

        fsm.state.value shouldBe GoogleConnectionState.Unlinked

        fsm.checkConnectionOnStartup()

        fsm.state.value shouldBe GoogleConnectionState.Unlinked
    }

    test("Startup Connection: should remain Linked if current access token is valid") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("valid-token", "refresh-token")

        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)
        coEvery { driveService.validateConnection("valid-token") } returns true

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
        fsm.driveService = driveService

        fsm.state.value shouldBe GoogleConnectionState.Linked

        fsm.checkConnectionOnStartup()

        fsm.state.value shouldBe GoogleConnectionState.Linked
        tokenRepo.getAccessToken() shouldBe "valid-token"
    }

    test("Startup Connection: should update tokens and remain Linked if access token is invalid but refresh succeeds") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("expired-token", "refresh-token")

        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)
        coEvery { driveService.validateConnection("expired-token") } returns false
        coEvery { authService.refreshAccessToken("refresh-token") } returns "new-token"

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
        fsm.driveService = driveService

        fsm.checkConnectionOnStartup()

        fsm.state.value shouldBe GoogleConnectionState.Linked
        tokenRepo.getAccessToken() shouldBe "new-token"
    }

    test("Startup Connection: should disconnect if access token is invalid, refresh fails, and online") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("expired-token", "refresh-token")

        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)
        coEvery { driveService.validateConnection("expired-token") } returns false
        coEvery { authService.refreshAccessToken("refresh-token") } returns null

        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { respond("OK") }))
        fsm.driveService = driveService

        fsm.checkConnectionOnStartup()

        fsm.state.value shouldBe GoogleConnectionState.Unlinked
        tokenRepo.hasTokens() shouldBe false
    }

    test("Startup Connection: should keep Linked if access token is invalid, refresh fails, but offline") {
        val settings = MapSettings()
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens("expired-token", "refresh-token")

        val authService = mockk<GoogleAuthService>(relaxed = true)
        val driveService = mockk<GoogleDriveService>(relaxed = true)
        coEvery { driveService.validateConnection("expired-token") } returns false
        coEvery { authService.refreshAccessToken("refresh-token") } returns null

        // Simulate offline by throwing a network exception
        val fsm = GoogleAccountFlow(authService, tokenRepo, HttpClient(MockEngine { throw Exception("No Internet") }))
        fsm.driveService = driveService

        fsm.checkConnectionOnStartup()

        fsm.state.value shouldBe GoogleConnectionState.Linked
        tokenRepo.getAccessToken() shouldBe "expired-token"
    }
})
