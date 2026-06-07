package com.borinquenterrier.cef

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlin.test.*

class AgentStreamTest {

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
}
