package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class GoogleTokenServiceTest : FunSpec({

    val tokenRepository = mockk<GoogleTokenRepository>(relaxed = true)
    val authService = mockk<GoogleAuthService>(relaxed = true)
    val service = GoogleTokenService(tokenRepository, authService)

    context("withToken — happy path") {

        test("calls block with current access token") {
            coEvery { tokenRepository.getAccessToken() } returns "tok-abc"
            var captured = ""
            service.withToken { tok -> captured = tok }
            captured shouldBe "tok-abc"
        }
    }

    context("withToken — unauthenticated") {

        test("throws when no access token stored") {
            coEvery { tokenRepository.getAccessToken() } returns null
            val ex = shouldThrow<Exception> { service.withToken { "ok" } }
            ex.message shouldContain "Not authenticated"
        }
    }

    context("withToken — 401 with refresh token") {

        test("retries block with new token after successful refresh") {
            var callCount = 0
            coEvery { tokenRepository.getAccessToken() } returns "expired-tok"
            coEvery { tokenRepository.getRefreshToken() } returns "refresh-tok"
            coEvery { authService.refreshAccessToken("refresh-tok") } returns "fresh-tok"

            val result = service.withToken { tok ->
                callCount++
                if (callCount == 1) throw GoogleApiException(401, "Unauthorized")
                tok
            }

            result shouldBe "fresh-tok"
            coVerify(exactly = 1) { authService.refreshAccessToken("refresh-tok") }
            coVerify(exactly = 1) { tokenRepository.saveTokens("fresh-tok", "refresh-tok") }
        }

        test("rethrows 401 when no refresh token is stored") {
            coEvery { tokenRepository.getAccessToken() } returns "expired-tok"
            coEvery { tokenRepository.getRefreshToken() } returns null

            val ex = shouldThrow<GoogleApiException> {
                service.withToken<Unit> { throw GoogleApiException(401, "Unauthorized") }
            }
            ex.statusCode shouldBe 401
        }

        test("rethrows 401 when refreshAccessToken returns null") {
            coEvery { tokenRepository.getAccessToken() } returns "expired-tok"
            coEvery { tokenRepository.getRefreshToken() } returns "refresh-tok"
            coEvery { authService.refreshAccessToken("refresh-tok") } returns null

            val ex = shouldThrow<GoogleApiException> {
                service.withToken<Unit> { throw GoogleApiException(401, "Unauthorized") }
            }
            ex.statusCode shouldBe 401
        }

        test("retry also returns 401 — throws session-expired Exception") {
            coEvery { tokenRepository.getAccessToken() } returns "tok"
            coEvery { tokenRepository.getRefreshToken() } returns "refresh-tok"
            coEvery { authService.refreshAccessToken("refresh-tok") } returns "fresh-tok"

            val ex = shouldThrow<Exception> {
                service.withToken<Unit> { throw GoogleApiException(401, "Unauthorized") }
            }
            ex.message shouldContain "session expired"
        }
    }

    context("withToken — non-401 error") {

        test("rethrows non-401 GoogleApiException as-is") {
            coEvery { tokenRepository.getAccessToken() } returns "tok"

            val ex = shouldThrow<GoogleApiException> {
                service.withToken<Unit> { throw GoogleApiException(403, "Forbidden") }
            }
            ex.statusCode shouldBe 403
        }

        test("rethrows 500 GoogleApiException as-is") {
            coEvery { tokenRepository.getAccessToken() } returns "tok"

            val ex = shouldThrow<GoogleApiException> {
                service.withToken<Unit> { throw GoogleApiException(500, "Server error") }
            }
            ex.statusCode shouldBe 500
        }
    }
})
