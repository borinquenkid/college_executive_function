package com.borinquenterrier.cef

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Tests for GET /api/sources/{id}/stream (ADR 0012). */
class SourceStreamTest {

    /** Parses each `data: {...}` line and returns (type, status-if-SOURCE_STATUS) pairs in order. */
    private fun eventsOf(body: String): List<Pair<String, String?>> =
        body.lineSequence()
            .filter { it.startsWith("data: ") }
            .map {
                val json = Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject
                val type = json["type"]!!.jsonPrimitive.content
                val status = if (type == "SOURCE_STATUS") json["data"]!!.jsonObject["status"]!!.jsonPrimitive.content else null
                type to status
            }
            .toList()

    @Test
    fun testStreamShowsCurrentPhaseImmediatelyThenClosesOnDone() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo

        // A subscriber connecting mid-digestion (the flow starts already past PENDING) must see
        // that current phase as its first event, not just whatever comes next.
        val statusFlow = MutableStateFlow(SourceStatus.ANALYZING_CONTEXT)
        coEvery { mockSourceRepo.statusFlow("src-1") } returns statusFlow

        // Drives the flow to a terminal value shortly after the request starts — long enough that
        // the server has already subscribed and captured ANALYZING_CONTEXT as its first value.
        CoroutineScope(Dispatchers.Default).launch {
            delay(200)
            statusFlow.value = SourceStatus.EXTRACTING_DELIVERABLES
            delay(50)
            statusFlow.value = SourceStatus.DONE
        }

        application {
            module(mockContainer)
        }

        val response = withTimeout(5000) {
            client.get("/api/sources/src-1/stream") {
                header(HttpHeaders.Accept, "text/event-stream")
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val events = eventsOf(response.bodyAsText())
        val statuses = events.mapNotNull { it.second }

        assertEquals(
            listOf("ANALYZING_CONTEXT", "EXTRACTING_DELIVERABLES", "DONE"),
            statuses,
            "First status must be the phase already current at subscribe time, not just future transitions"
        )
        // Stream closes cleanly on DONE: RUN_FINISHED is the last event, and DONE is only emitted once.
        assertEquals("RUN_FINISHED", events.last().first)
        assertEquals(1, statuses.count { it == "DONE" })
    }

    @Test
    fun testStreamClosesOnFailedTooNotJustDone() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo

        val statusFlow = MutableStateFlow(SourceStatus.RESOLVING_CONFLICTS)
        coEvery { mockSourceRepo.statusFlow("src-2") } returns statusFlow

        CoroutineScope(Dispatchers.Default).launch {
            delay(300)
            statusFlow.value = SourceStatus.FAILED
        }

        application {
            module(mockContainer)
        }

        val response = withTimeout(5000) {
            client.get("/api/sources/src-2/stream") {
                header(HttpHeaders.Accept, "text/event-stream")
            }
        }

        val events = eventsOf(response.bodyAsText())
        val statuses = events.mapNotNull { it.second }

        assertEquals(listOf("RESOLVING_CONFLICTS", "FAILED"), statuses)
        assertEquals("RUN_FINISHED", events.last().first)
    }

    @Test
    fun testStreamEmitsErrorWhenNoStatusEverRecorded() = testApplication {
        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        val mockSourceRepo = mockk<SqlDelightSourceRepository>(relaxed = true)
        every { mockContainer.sourceRepository } returns mockSourceRepo
        coEvery { mockSourceRepo.statusFlow("unknown-source") } returns null

        application {
            module(mockContainer)
        }

        val response = withTimeout(5000) {
            client.get("/api/sources/unknown-source/stream") {
                header(HttpHeaders.Accept, "text/event-stream")
            }
        }

        val events = eventsOf(response.bodyAsText())
        assertEquals(listOf("ERROR", "RUN_FINISHED"), events.map { it.first })
    }
}
