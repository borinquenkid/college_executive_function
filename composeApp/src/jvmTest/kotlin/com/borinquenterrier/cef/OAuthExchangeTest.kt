package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class OAuthExchangeBranchTest : FunSpec({

    val jsonHeader = headersOf("Content-Type", ContentType.Application.Json.toString())

    fun makeClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    val fullTokenJson = """{"access_token":"acc-123","refresh_token":"ref-456","expires_in":3600}"""
    val refreshJson = """{"access_token":"acc-new","expires_in":3600}"""

    // ── exchangeCodeForTokens ─────────────────────────────────────────────────

    test("exchangeCodeForTokens with clientSecret null returns parsed token") {
        val engine = MockEngine { _ -> respond(fullTokenJson, HttpStatusCode.OK, jsonHeader) }
        val result = OAuthExchange(makeClient(engine)).exchangeCodeForTokens(
            code = "auth-code",
            clientId = "client-id",
            clientSecret = null,
            redirectUri = "https://example.com/callback"
        )
        result.access_token shouldBe "acc-123"
        result.refresh_token shouldBe "ref-456"
        result.expires_in shouldBe 3600
    }

    test("exchangeCodeForTokens with clientSecret non-null returns parsed token") {
        val engine = MockEngine { _ -> respond(fullTokenJson, HttpStatusCode.OK, jsonHeader) }
        val result = OAuthExchange(makeClient(engine)).exchangeCodeForTokens(
            code = "auth-code",
            clientId = "client-id",
            clientSecret = "my-secret",
            redirectUri = "https://example.com/callback"
        )
        result.access_token shouldBe "acc-123"
    }

    test("exchangeCodeForTokens throws on non-2xx response") {
        val engine = MockEngine { _ ->
            respond("invalid_grant", HttpStatusCode.BadRequest, jsonHeader)
        }
        val ex = shouldThrow<Exception> {
            OAuthExchange(makeClient(engine)).exchangeCodeForTokens(
                code = "bad-code",
                clientId = "client-id",
                clientSecret = null,
                redirectUri = "https://example.com/callback"
            )
        }
        ex.message shouldContain "Failed to exchange"
        ex.message shouldContain "invalid_grant"
    }

    // ── refreshAccessToken ────────────────────────────────────────────────────

    test("refreshAccessToken with clientSecret null returns parsed token") {
        val engine = MockEngine { _ -> respond(refreshJson, HttpStatusCode.OK, jsonHeader) }
        val result = OAuthExchange(makeClient(engine)).refreshAccessToken(
            refreshToken = "ref-token",
            clientId = "client-id",
            clientSecret = null
        )
        result.access_token shouldBe "acc-new"
        result.refresh_token shouldBe null
    }

    test("refreshAccessToken with clientSecret non-null returns parsed token") {
        val engine = MockEngine { _ -> respond(refreshJson, HttpStatusCode.OK, jsonHeader) }
        val result = OAuthExchange(makeClient(engine)).refreshAccessToken(
            refreshToken = "ref-token",
            clientId = "client-id",
            clientSecret = "my-secret"
        )
        result.access_token shouldBe "acc-new"
    }

    test("refreshAccessToken throws on non-2xx response") {
        val engine = MockEngine { _ ->
            respond("token_expired", HttpStatusCode.Unauthorized, jsonHeader)
        }
        val ex = shouldThrow<Exception> {
            OAuthExchange(makeClient(engine)).refreshAccessToken(
                refreshToken = "expired-token",
                clientId = "client-id",
                clientSecret = null
            )
        }
        ex.message shouldContain "Failed to exchange"
    }

    // ── TokenResponse: null refresh_token field ───────────────────────────────

    test("TokenResponse with absent refresh_token deserializes to null") {
        val engine = MockEngine { _ ->
            respond("""{"access_token":"acc","expires_in":3600}""", HttpStatusCode.OK, jsonHeader)
        }
        val result = OAuthExchange(makeClient(engine)).refreshAccessToken(
            refreshToken = "ref", clientId = "cid", clientSecret = null
        )
        result.refresh_token shouldBe null
        result.expires_in shouldBe 3600
    }

    // ── refreshAccessToken with clientSecret non-null on error ────────────────

    test("refreshAccessToken with clientSecret non-null throws on non-2xx") {
        val engine = MockEngine { _ ->
            respond("unauthorized", HttpStatusCode.Unauthorized, jsonHeader)
        }
        val ex = shouldThrow<Exception> {
            OAuthExchange(makeClient(engine)).refreshAccessToken(
                refreshToken = "ref",
                clientId = "cid",
                clientSecret = "secret"
            )
        }
        ex.message shouldContain "Failed to exchange"
    }
})
