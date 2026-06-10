package com.borinquenterrier.cef

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

suspend fun getAllSourceItems(container: DependencyContainer): List<SourceItem> {
    val entities = try {
        container.sourceRepository.getAllSources()
    } catch (e: Exception) {
        emptyList()
    }
    
    return entities.map { entity ->
        val fragments = try {
            container.sourceRepository.getFragmentsForSource(entity.id).map { frag ->
                SourceFragment(
                    text = frag.text,
                    pageNumber = frag.pageNumber?.toInt(),
                    sectionTitle = frag.sectionTitle,
                    type = SourceType.valueOf(frag.type)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        
        SourceItem(
            title = entity.title,
            fragments = fragments,
            category = SourceCategory.valueOf(entity.category)
        )
    }
}

fun Application.module(testContainer: DependencyContainer? = null) {
    val getContainer = {
        testContainer ?: ServerContainer.container
    }

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }

        get("/api/sources") {
            WebIngestionController.handleGetSources(call, getContainer())
        }

        post("/api/sources") {
            WebIngestionController.handlePostSource(call, getContainer())
        }

        delete("/api/sources/{id}") {
            val id = call.parameters["id"] ?: ""
            WebIngestionController.handleDeleteSource(call, id, getContainer())
        }

        get("/api/events") {
            WebIngestionController.handleGetEvents(call, getContainer())
        }

        post("/api/events/sync") {
            WebIngestionController.handleSyncEvents(call, getContainer())
        }

        get("/api/settings") {
            WebIngestionController.handleGetSettings(call, getContainer())
        }

        post("/api/settings") {
            WebIngestionController.handleSaveSettings(call, getContainer())
        }

        get("/api/auth/google/status") {
            WebIngestionController.handleGetGoogleAuthStatus(call, getContainer())
        }

        get("/api/calendars") {
            WebIngestionController.handleGetCalendars(call, getContainer())
        }

        post("/api/calendars") {
            WebIngestionController.handleCreateCalendar(call, getContainer())
        }

        
        get("/api/agent/stream") {
            val query = call.request.queryParameters["query"] ?: ""
            call.response.cacheControl(io.ktor.http.CacheControl.NoCache(null))
            
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                try {
                    // 1. RUN_STARTED
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"RUN_STARTED\",\"timestamp\":1717720000000,\"data\":{\"runId\":\"test-run\"}}\n\n")
                    flush()
                    
                    // 2. REASONING_DELTA
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"REASONING_DELTA\",\"timestamp\":1717720000000,\"data\":{\"text\":\"Retrieving relevant course documents and syllabi...\"}}\n\n")
                    flush()
                    delay(50)
                    
                    // 3. TOOL_CALL_START
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"TOOL_CALL_START\",\"timestamp\":1717720000000,\"data\":{\"toolName\":\"queryAllSources\",\"arguments\":\"{\\\"query\\\":\\\"$query\\\"}\"}}\n\n")
                    flush()
                    delay(50)
                    
                    // Invoke KMP RAG query logic (either mocked or real)
                    val container = getContainer()
                    val sources = getAllSourceItems(container)
                    val responseText = try {
                        container.contextAgent.queryAllSources(sources, emptyList(), query)
                    } catch (e: Throwable) {
                        "Error querying context agent: ${e.message}"
                    }
                    
                    // 4. TOOL_CALL_RESULT
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"TOOL_CALL_RESULT\",\"timestamp\":1717720000000,\"data\":{\"toolName\":\"queryAllSources\",\"success\":true}}\n\n")
                    flush()
                    delay(50)
                    
                    // 5. TEXT_MESSAGE_DELTA (Stream the final answer)
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"TEXT_MESSAGE_DELTA\",\"timestamp\":1717720000000,\"data\":{\"text\":\"$responseText\"}}\n\n")
                    flush()
                    delay(50)
                } catch (e: Throwable) {
                    println("STREAM ERROR: ${e.message}")
                    e.printStackTrace()
                    // Emit a fallback error event
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"ERROR\",\"timestamp\":1717720000000,\"data\":{\"message\":\"${e.message}\"}}\n\n")
                    flush()
                } finally {
                    // 6. RUN_FINISHED
                    writeStringUtf8("event: message\n")
                    writeStringUtf8("data: {\"type\":\"RUN_FINISHED\",\"timestamp\":1717720000000,\"data\":{}}\n\n")
                    flush()
                }
            }
        }
    }
}