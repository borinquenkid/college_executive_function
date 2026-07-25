package com.borinquenterrier.cef

import com.borinquenterrier.cef.lti.LtiTestSupport
import com.borinquenterrier.cef.lti.loginViaLti
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TaskDecompositionStreamTest {

    private fun eventTypesOf(body: String): List<String> =
        body.lineSequence()
            .filter { it.startsWith("data: ") }
            .map { Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject["type"]!!.jsonPrimitive.content }
            .toList()

    private fun dataOf(body: String, type: String) =
        body.lineSequence()
            .filter { it.startsWith("data: ") }
            .map { Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject }
            .first { it["type"]!!.jsonPrimitive.content == type }["data"]!!.jsonObject

    private val createdFactories = mutableListOf<ServerContainerFactory>()

    private fun newFactory(): ServerContainerFactory {
        val baseDir = Files.createTempDirectory("cef-decompose-stream-test").toFile()
        return ServerContainerFactory(tenantBaseDir = baseDir.absolutePath).also { createdFactories += it }
    }

    @AfterTest
    fun tearDown() = runBlocking {
        createdFactories.forEach { it.closeAll() }
        createdFactories.clear()
    }

    @Test
    fun testDecomposeStreamEmitsStateSnapshotWithDecomposedTasks() = testApplication {
        val targetEvent = DayEvent(
            id = "evt-1",
            title = "Term Paper",
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.DEADLINE,
            date = LocalDate(2026, 12, 1)
        )
        val tasks = listOf(
            DecomposedTask("Draft outline", daysBeforeDue = 7, description = "Quotes \"go here\"\nand a newline"),
            DecomposedTask("Write draft", daysBeforeDue = 3, description = "")
        )

        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        coEvery { mockCalendarAgent.getEvents("default") } returns listOf(targetEvent)

        val mockDecompositionService = mockk<TaskDecompositionService>(relaxed = true)
        coEvery { mockDecompositionService.decompose(targetEvent) } returns tasks

        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        every { mockContainer.calendarAgent } returns mockCalendarAgent
        every { mockContainer.taskDecompositionService } returns mockDecompositionService

        application {
            module(mockContainer)
        }

        val response = client.get("/api/events/evt-1/decompose/stream") {
            header(HttpHeaders.Accept, "text/event-stream")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val types = eventTypesOf(body)
        assertTrue(types.contains("RUN_STARTED"))
        assertTrue(types.contains("TOOL_CALL_START"))
        assertTrue(types.contains("STATE_SNAPSHOT"))
        assertTrue(types.contains("RUN_FINISHED"))

        val decomposedTasks = dataOf(body, "STATE_SNAPSHOT")["decomposedTasks"]!!.jsonArray
        assertEquals(2, decomposedTasks.size)
        assertEquals("Draft outline", decomposedTasks[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(7, decomposedTasks[0].jsonObject["daysBeforeDue"]!!.jsonPrimitive.content.toInt())
        assertEquals(
            "Quotes \"go here\"\nand a newline",
            decomposedTasks[0].jsonObject["description"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun testDecomposeStreamEmitsErrorWhenEventNotFound() = testApplication {
        val mockCalendarAgent = mockk<CalendarAgent>(relaxed = true)
        coEvery { mockCalendarAgent.getEvents("default") } returns emptyList()

        val mockContainer = mockk<DependencyContainer>(relaxed = true)
        every { mockContainer.calendarAgent } returns mockCalendarAgent

        application {
            module(mockContainer)
        }

        val response = client.get("/api/events/missing-id/decompose/stream") {
            header(HttpHeaders.Accept, "text/event-stream")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val types = eventTypesOf(body)
        assertTrue(types.contains("ERROR"))
        assertTrue(types.contains("RUN_FINISHED"))
        assertFalse(types.contains("STATE_SNAPSHOT"))
    }

    @Test
    fun testDecomposeStreamRequiresAuth() = testApplication {
        val factory = newFactory()
        application {
            module(
                containerFactory = { studentId -> factory.containerFor(studentId) },
                ltiPlatformConfig = LtiTestSupport.config,
                ltiVerifier = LtiTestSupport.verifier(),
                directoryDatabase = factory.directoryDatabase,
                dbFactory = factory.dbFactory,
                appBaseUrl = "https://test.example.edu"
            )
        }
        val client = createClient { install(HttpCookies) }

        val unauthed = client.get("/api/events/evt-1/decompose/stream")
        assertEquals(HttpStatusCode.Unauthorized, unauthed.status)

        client.loginViaLti()
        val authed = client.get("/api/events/evt-1/decompose/stream") {
            header(HttpHeaders.Accept, "text/event-stream")
        }
        assertEquals(HttpStatusCode.OK, authed.status)
    }
}
