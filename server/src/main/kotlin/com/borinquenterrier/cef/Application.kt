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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.io.File

fun main() {
    startScheduledBackupIfConfigured()
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/** Out-of-the-box backups: set CEF_BACKUP_DIR to enable, no host cron required.
 *  See DEPLOYMENT.md. */
private fun startScheduledBackupIfConfigured() {
    val backupDir = System.getenv("CEF_BACKUP_DIR") ?: return
    val tenantBaseDir = System.getenv("CEF_TENANT_BASE_DIR")
        ?: File(System.getProperty("user.home"), ".cef/tenants").absolutePath
    val intervalHours = System.getenv("CEF_BACKUP_INTERVAL_HOURS")?.toLongOrNull() ?: 24L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ScheduledBackupJob(tenantBaseDir, backupDir, intervalHours).start(scope)
    println("[main] Scheduled backups enabled: every ${intervalHours}h to $backupDir")
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

/** Matches the tenant-directory naming TenantDatabaseFactory/TenantConnectionCache build file
 *  paths from. Rejecting anything else here — before a studentId ever reaches those factories —
 *  is what stops a crafted X-Student-ID header (e.g. "../../../etc/passwd") from escaping the
 *  tenant data mount via path traversal. */
private val studentIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

private suspend fun ApplicationCall.resolveStudentId(): String? {
    val header = request.headers["X-Student-ID"]?.takeIf { it.isNotBlank() } ?: return "default"
    if (!studentIdPattern.matches(header)) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid X-Student-ID header"))
        return null
    }
    return header
}

fun Application.module(
    testContainer: DependencyContainer? = null,
    containerFactory: suspend (String) -> DependencyContainer = { studentId -> ServerContainer.containerFor(studentId) }
) {
    suspend fun resolveContainer(call: ApplicationCall): DependencyContainer? {
        testContainer?.let { return it }
        val studentId = call.resolveStudentId() ?: return null
        return containerFactory(studentId)
    }

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }

        get("/api/sources") {
            val container = resolveContainer(call) ?: return@get
            WebIngestionController.handleGetSources(call, container)
        }

        post("/api/sources") {
            val container = resolveContainer(call) ?: return@post
            WebIngestionController.handlePostSource(call, container)
        }

        delete("/api/sources/{id}") {
            val container = resolveContainer(call) ?: return@delete
            val id = call.parameters["id"] ?: ""
            WebIngestionController.handleDeleteSource(call, id, container)
        }

        get("/api/events") {
            val container = resolveContainer(call) ?: return@get
            WebIngestionController.handleGetEvents(call, container)
        }

        post("/api/events/sync") {
            val container = resolveContainer(call) ?: return@post
            WebIngestionController.handleSyncEvents(call, container)
        }

        get("/api/settings") {
            val container = resolveContainer(call) ?: return@get
            WebIngestionController.handleGetSettings(call, container)
        }

        post("/api/settings") {
            val container = resolveContainer(call) ?: return@post
            WebIngestionController.handleSaveSettings(call, container)
        }

        get("/api/auth/google/status") {
            val container = resolveContainer(call) ?: return@get
            WebIngestionController.handleGetGoogleAuthStatus(call, container)
        }

        get("/api/calendars") {
            val container = resolveContainer(call) ?: return@get
            WebIngestionController.handleGetCalendars(call, container)
        }

        post("/api/calendars") {
            val container = resolveContainer(call) ?: return@post
            WebIngestionController.handleCreateCalendar(call, container)
        }

        get("/api/agent/stream") {
            val container = resolveContainer(call) ?: return@get
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