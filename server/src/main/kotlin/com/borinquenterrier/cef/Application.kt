package com.borinquenterrier.cef

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.utils.io.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import java.io.File
import java.security.SecureRandom
import java.util.Base64

fun main() {
    startScheduledBackupIfConfigured()
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/** Out-of-the-box backups: set CEF_BACKUP_DIR to enable, no host cron required.
 *  See DEPLOYMENT.md. */
private fun startScheduledBackupIfConfigured() {
    val backupDir = System.getenv("CEF_BACKUP_DIR") ?: return
    val tenantBaseDir = resolveTenantBaseDir()
    val intervalHours = System.getenv("CEF_BACKUP_INTERVAL_HOURS")?.toLongOrNull() ?: 24L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ScheduledBackupJob(tenantBaseDir, backupDir, intervalHours).start(scope)
    println("[main] Scheduled backups enabled: every ${intervalHours}h to $backupDir")
}

/** Same resolution ServerContainer.kt uses for the tenant-data mount; duplicated here (and in
 *  BackupCli.kt) rather than shared, matching this codebase's existing pattern for these small
 *  env-var lookups. */
private fun resolveTenantBaseDir(): String =
    System.getenv("CEF_TENANT_BASE_DIR")
        ?: File(System.getProperty("user.home"), ".cef/tenants").absolutePath

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
 *  is what stops a crafted session studentId from escaping the tenant data mount via path
 *  traversal. A signed session cookie can't be forged without the session secret, so this is a
 *  defensive backstop, not the primary access control. */
private val studentIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

private const val SESSION_COOKIE_NAME = "CEF_SESSION"

@kotlinx.serialization.Serializable
data class UserSession(val studentId: String)

/** Random, unguessable per-student identity — this is the "obscurity" credential: knowing it is
 *  the only thing that grants access, there's no separate password. Base64 URL-safe alphabet
 *  (A-Za-z0-9-_) is a strict subset of studentIdPattern, so it always validates. */
private fun generateStudentId(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return "u-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private suspend fun ApplicationCall.resolveStudentId(): String? {
    val studentId = sessions.get<UserSession>()?.studentId
    if (studentId == null || !studentIdPattern.matches(studentId)) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "No active session. Call POST /api/auth/start first."))
        return null
    }
    return studentId
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

    // Skipped for the mocked-container test path (resolveContainer never touches sessions there),
    // which avoids installing a real signing secret / rate limiter for route-level unit tests.
    if (testContainer == null) {
        install(XForwardedHeaders)

        install(Sessions) {
            cookie<UserSession>(SESSION_COOKIE_NAME) {
                cookie.httpOnly = true
                cookie.path = "/"
                cookie.maxAgeInSeconds = 60L * 60 * 24 * 180
                cookie.extensions["SameSite"] = "Lax"
                // Plain HTTP is DEPLOYMENT.md's documented default (docker compose up, port 80,
                // no TLS) — forcing Secure=true there would silently break login. Operators who've
                // put TLS in front opt in explicitly.
                if (System.getenv("CEF_FORCE_SECURE_COOKIES") == "true") {
                    cookie.secure = true
                }
                transform(SessionTransportTransformerMessageAuthentication(SessionSecret.resolve(resolveTenantBaseDir())))
            }
        }

        install(RateLimit) {
            register(RateLimitName("auth-start")) {
                rateLimiter(limit = 5, refillPeriod = 60.seconds)
                requestKey { call -> call.request.origin.remoteHost }
            }
        }
    }

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }

        if (testContainer == null) {
            rateLimit(RateLimitName("auth-start")) {
                post("/api/auth/start") {
                    val existing = call.sessions.get<UserSession>()
                    if (existing != null) {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                        return@post
                    }
                    call.sessions.set(UserSession(generateStudentId()))
                    call.respond(HttpStatusCode.OK, mapOf("status" to "created"))
                }
            }

            post("/api/auth/logout") {
                call.sessions.clear<UserSession>()
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            }
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