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
import kotlinx.coroutines.withContext
import kotlin.time.Clock
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
                suspend fun emit(type: String, dataJson: String) {
                    writeStringUtf8("event: message\n")
                    writeStringUtf8(
                        "data: {\"type\":\"$type\",\"timestamp\":${Clock.System.now().toEpochMilliseconds()}," +
                            "\"data\":$dataJson}\n\n"
                    )
                    flush()
                }

                val runId = randomHexId(8)
                try {
                    emit("RUN_STARTED", "{\"runId\":\"$runId\"}")

                    emit(
                        "REASONING_DELTA",
                        "{\"text\":\"Retrieving relevant course documents and syllabi...\"}"
                    )

                    emit(
                        "TOOL_CALL_START",
                        "{\"toolName\":\"queryAllSources\",\"arguments\":\"{\\\"query\\\":\\\"" +
                            "${query.escapeJsonString()}\\\"}\"}"
                    )

                    // Surface the actor/critique passes CriticActorAIService runs under the hood as
                    // their own tool-call event groups, instead of one opaque queryAllSources call.
                    val progressListener = CriticProgressListener { phase ->
                        when (phase) {
                            CriticPhase.ACTOR_START ->
                                emit("TOOL_CALL_START", "{\"toolName\":\"actorPass\"}")
                            CriticPhase.ACTOR_DONE ->
                                emit("TOOL_CALL_RESULT", "{\"toolName\":\"actorPass\",\"success\":true}")
                            CriticPhase.CRITIQUE_START -> {
                                emit("REASONING_DELTA", "{\"text\":\"Reviewing the answer for accuracy...\"}")
                                emit("TOOL_CALL_START", "{\"toolName\":\"critiquePass\"}")
                            }
                            CriticPhase.CRITIQUE_DONE ->
                                emit("TOOL_CALL_RESULT", "{\"toolName\":\"critiquePass\",\"success\":true}")
                        }
                    }

                    val sources = getAllSourceItems(container)
                    val responseText = try {
                        withContext(CriticProgressContext(progressListener)) {
                            container.contextAgent.queryAllSources(sources, emptyList(), query)
                        }
                    } catch (e: Throwable) {
                        "Error querying context agent: ${e.message}"
                    }

                    emit("TOOL_CALL_RESULT", "{\"toolName\":\"queryAllSources\",\"success\":true}")

                    emit("TEXT_MESSAGE_DELTA", "{\"text\":\"${responseText.escapeJsonString()}\"}")
                } catch (e: Throwable) {
                    println("STREAM ERROR: ${e.message}")
                    e.printStackTrace()
                    emit("ERROR", "{\"message\":\"${(e.message ?: "").escapeJsonString()}\"}")
                } finally {
                    emit("RUN_FINISHED", "{}")
                }
            }
        }
    }
}