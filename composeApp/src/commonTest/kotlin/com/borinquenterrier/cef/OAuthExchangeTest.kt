package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class OAuthExchangeTest {

    @Test
    fun `exchangeCodeForTokens makes correct request and parses response`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals("https://oauth2.googleapis.com/token", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)

            respond(
                content = ByteReadChannel("""{"access_token":"mock-token","expires_in":3600}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
        }
        val oauthExchange = OAuthExchange(client)

        val response = oauthExchange.exchangeCodeForTokens(
            code = "test-code",
            clientId = "test-id",
            clientSecret = null,
            redirectUri = "test-uri"
        )

        assertEquals("mock-token", response.access_token)
        assertEquals(3600, response.expires_in)
    }
}
