package com.borinquenterrier.cef

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentStreamTest {

    /** Parses each `data: {...}` line of an SSE body and returns the decoded "type" field in order. */
    private fun eventTypesOf(body: String): List<String> =
        body.lineSequence()
            .filter { it.startsWith("data: ") }
            .map { Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject["type"]!!.jsonPrimitive.content }
            .toList()

    @Test
    fun testAgentStreamEndpointExistsAndStreamsLifecycle() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/api/agent/stream?query=hello") {
            header(HttpHeaders.Accept, "text/event-stream")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        val contentType = response.contentType()
        assertNotNull(contentType, "Content-Type header should not be null")
        assertEquals("text", contentType.contentType)
        assertEquals("event-stream", contentType.contentSubtype)
        
        val body = response.bodyAsText()
        assertTrue(body.contains("RUN_STARTED"), "Expected body to contain RUN_STARTED event")
        assertTrue(body.contains("RUN_FINISHED"), "Expected body to contain RUN_FINISHED event")
    }

    @Test
    fun testAgentStreamQueriesContextAgentAndStreamsEvents() = testApplication {
        // Create Mock ContextAgent
        val mockContextAgent = mockk<ContextAgent>(relaxed = true)
        
        // Mock queryAllSources to return a fixed string
        coEvery { 
            mockContextAgent.queryAllSources(any(), any(), "homework") 
        } returns "This is a mocked RAG response."
        
        // Setup a mock/test DependencyContainer
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        every { mockContainer.contextAgent } returns mockContextAgent
        
        application {
            // Pass the mock container to the module
            module(mockContainer)
        }
        
        val response = client.get("/api/agent/stream?query=homework") {
            header(HttpHeaders.Accept, "text/event-stream")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        
        // Verify reasoning and tool call logs are streamed
        assertTrue(body.contains("REASONING_DELTA"), "Expected stream to contain reasoning events")
        assertTrue(body.contains("TOOL_CALL_START"), "Expected stream to contain tool call logs")
        assertTrue(body.contains("This is a mocked RAG response."), "Expected stream to contain final streamed response")
        assertTrue(body.contains("RUN_FINISHED"), "Expected stream to end with RUN_FINISHED")
    }

    @Test
    fun testAgentStreamEmitsOnlyValidJsonDataLines() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/agent/stream?query=hello") {
            header(HttpHeaders.Accept, "text/event-stream")
        }

        val body = response.bodyAsText()
        val dataLines = body.lineSequence().filter { it.startsWith("data: ") }.toList()
        assertTrue(dataLines.isNotEmpty(), "Expected at least one data line")
        dataLines.forEach { line ->
            // Throws if a field (e.g. an unescaped quote/newline in a response) breaks JSON parsing.
            Json.parseToJsonElement(line.removePrefix("data: "))
        }
    }

    @Test
    fun testAgentStreamEscapesQuotesAndNewlinesInQueryAndResponse() = testApplication {
        val mockContextAgent = mockk<ContextAgent>(relaxed = true)
        coEvery { mockContextAgent.queryAllSources(any(), any(), any()) } returns
            "Line one\nLine \"two\" with a backslash \\ here."

        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        every { mockContainer.contextAgent } returns mockContextAgent

        application {
            module(mockContainer)
        }

        val response = client.get("/api/agent/stream?query=${"a \"tricky\" query".encodeURLQueryComponent()}") {
            header(HttpHeaders.Accept, "text/event-stream")
        }

        val body = response.bodyAsText()
        val dataLines = body.lineSequence().filter { it.startsWith("data: ") }.toList()
        dataLines.forEach { line -> Json.parseToJsonElement(line.removePrefix("data: ")) }
        assertTrue(body.contains("Line one\\nLine \\\"two\\\" with a backslash \\\\ here."))
    }

    @Test
    fun testAgentStreamRunIdIsUniquePerRequest() = testApplication {
        application {
            module()
        }

        fun runIdOf(body: String): String {
            val runStarted = body.lineSequence().first { it.startsWith("data: ") && it.contains("RUN_STARTED") }
            return Json.parseToJsonElement(runStarted.removePrefix("data: "))
                .jsonObject["data"]!!.jsonObject["runId"]!!.jsonPrimitive.content
        }

        val first = runIdOf(client.get("/api/agent/stream?query=a") {
            header(HttpHeaders.Accept, "text/event-stream")
        }.bodyAsText())
        val second = runIdOf(client.get("/api/agent/stream?query=b") {
            header(HttpHeaders.Accept, "text/event-stream")
        }.bodyAsText())

        assertNotEquals(first, second, "Each request should get its own runId")
    }

    @Test
    fun testAgentStreamSurfacesActorAndCritiquePassesAsSeparateToolCallGroups() = testApplication {
        val mockContextAgent = mockk<ContextAgent>(relaxed = true)
        coEvery { mockContextAgent.queryAllSources(any(), any(), any()) } coAnswers {
            reportCriticProgress(CriticPhase.ACTOR_START)
            reportCriticProgress(CriticPhase.ACTOR_DONE)
            reportCriticProgress(CriticPhase.CRITIQUE_START)
            reportCriticProgress(CriticPhase.CRITIQUE_DONE)
            "final refined answer"
        }

        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        every { mockContainer.contextAgent } returns mockContextAgent

        application {
            module(mockContainer)
        }

        val response = client.get("/api/agent/stream?query=homework") {
            header(HttpHeaders.Accept, "text/event-stream")
        }

        val types = eventTypesOf(response.bodyAsText())
        // Two distinct TOOL_CALL_START/TOOL_CALL_RESULT groups (actor, critique) nested inside the
        // outer queryAllSources group — not a single fixed pass.
        val toolCallStarts = types.count { it == "TOOL_CALL_START" }
        val toolCallResults = types.count { it == "TOOL_CALL_RESULT" }
        assertEquals(3, toolCallStarts, "Expected queryAllSources + actorPass + critiquePass starts")
        assertEquals(3, toolCallResults, "Expected queryAllSources + actorPass + critiquePass results")
        assertTrue(types.contains("RUN_FINISHED"))
    }
}
