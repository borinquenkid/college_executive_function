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
import com.borinquenterrier.cef.lti.LtiLaunchHandler
import com.borinquenterrier.cef.lti.LtiLaunchVerifier
import com.borinquenterrier.cef.lti.LtiLoginHandler
import com.borinquenterrier.cef.lti.LtiPlatformConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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

/** This deployment's externally-reachable HTTPS origin (e.g. https://cef.university.edu, no
 *  trailing slash) — needed to build the LTI launch redirect_uri and, later, the Google web OAuth
 *  callback URL. LTI 1.3 requires HTTPS launch URIs, so unlike CEF_FORCE_SECURE_COOKIES this one
 *  isn't optional once LTI is the only login path (see DEPLOYMENT.md). */
private fun resolveAppBaseUrl(): String =
    System.getenv("CEF_APP_BASE_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
        ?: error("CEF_APP_BASE_URL is required — see DEPLOYMENT.md's LTI registration section.")

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
private const val STAFF_SESSION_COOKIE_NAME = "CEF_STAFF_SESSION"

/** Minted only by LtiLaunchHandler on a verified LTI launch — see docs/adr/0006-lti-1.3-only-auth.md.
 *  studentId is "lti-" + sha256("iss|deployment_id|sub"), not a random secret: the cookie itself
 *  (HMAC-signed, see SessionSecret) is what grants access, same as the old model, but now nothing
 *  can mint one without a platform's signature backing it. epoch pins this session to
 *  DirectoryDatabase's session_epoch at mint time — resolveStudentId() rejects a mismatch, which
 *  is how the staff console's "reset a student's session" (docs/adr/0007) actually revokes access
 *  despite the cookie itself being a self-contained, stateless signed token. */
@kotlinx.serialization.Serializable
data class UserSession(val studentId: String, val epoch: Int = 0)

/** Minted alongside UserSession when an LTI launch's roles claim includes Instructor/Administrator
 *  (see lti/LtiRoles.kt) — grants access to the /staff console (docs/adr/0007). */
@kotlinx.serialization.Serializable
data class StaffSession(val studentId: String)

private suspend fun ApplicationCall.resolveStudentId(directoryDatabase: DirectoryDatabase): String? {
    val session = sessions.get<UserSession>()
    val studentId = session?.studentId
    if (studentId == null || !studentIdPattern.matches(studentId) ||
        directoryDatabase.currentEpoch(studentId) != session.epoch
    ) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "No active session. Access this tool from your course in your institution's LMS."))
        return null
    }
    return studentId
}

private suspend fun ApplicationCall.resolveStaffStudentId(): String? {
    val studentId = sessions.get<StaffSession>()?.studentId
    if (studentId == null || !studentIdPattern.matches(studentId)) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Staff access required."))
        return null
    }
    return studentId
}

fun Application.module(
    testContainer: DependencyContainer? = null,
    containerFactory: suspend (String) -> DependencyContainer = { studentId -> ServerContainer.containerFor(studentId) },
    // Overridable so tests can point LTI at a local fake platform/JWKS instead of requiring
    // CEF_LTI_* env vars and a real IdP — see LtiLaunchIntegrationTest. ltiVerifier is separate
    // from ltiPlatformConfig because the default verifier construction reaches out to a real
    // JWKS URL over HTTPS; tests need to inject one backed by a fake JwkProvider instead.
    ltiPlatformConfig: LtiPlatformConfig? = null,
    ltiVerifier: LtiLaunchVerifier? = null,
    directoryDatabase: DirectoryDatabase? = null,
    dbFactory: TenantDatabaseFactory? = null,
    appBaseUrl: String? = null,
    // Unlike the LTI params above, an unresolved (null) value here is a legitimate outcome, not
    // just an unset-override marker — Google Calendar sync is optional (see GoogleWebOAuthConfig).
    // Tests that want it configured pass a service explicitly; tests that don't just naturally
    // get null, since CEF_GOOGLE_WEB_CLIENT_ID/SECRET are never set in the test process.
    googleWebOAuthService: GoogleWebOAuthService? = null
) {
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
            cookie<StaffSession>(STAFF_SESSION_COOKIE_NAME) {
                cookie.httpOnly = true
                cookie.path = "/"
                cookie.maxAgeInSeconds = 60L * 60 * 24 * 180
                cookie.extensions["SameSite"] = "Lax"
                if (System.getenv("CEF_FORCE_SECURE_COOKIES") == "true") {
                    cookie.secure = true
                }
                transform(SessionTransportTransformerMessageAuthentication(SessionSecret.resolve(resolveTenantBaseDir())))
            }
        }

        install(RateLimit) {
            register(RateLimitName("lti-login")) {
                rateLimiter(limit = 5, refillPeriod = 60.seconds)
                requestKey { call -> call.request.origin.remoteHost }
            }
        }
    }

    val resolvedLtiConfig = if (testContainer == null) ltiPlatformConfig ?: LtiPlatformConfig.resolveFromEnv() else null
    val resolvedLtiVerifier = if (testContainer == null) ltiVerifier ?: resolvedLtiConfig?.let { LtiLaunchVerifier(it) } else null
    val resolvedDirectoryDatabase = if (testContainer == null) directoryDatabase ?: ServerContainer.directoryDatabase else null
    val resolvedDbFactory = if (testContainer == null) dbFactory ?: ServerContainer.dbFactory else null
    val resolvedAppBaseUrl = if (testContainer == null) appBaseUrl ?: resolveAppBaseUrl() else null
    val resolvedGoogleWebOAuthService = if (testContainer == null) {
        googleWebOAuthService ?: GoogleWebOAuthConfig.resolveFromEnv()?.let {
            GoogleWebOAuthService(it, "$resolvedAppBaseUrl/api/auth/google/callback")
        }
    } else null

    suspend fun resolveContainer(call: ApplicationCall): DependencyContainer? {
        testContainer?.let { return it }
        val studentId = call.resolveStudentId(resolvedDirectoryDatabase!!) ?: return null
        return containerFactory(studentId)
    }

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }

        if (testContainer == null) {
            rateLimit(RateLimitName("lti-login")) {
                get("/lti/login") {
                    LtiLoginHandler.handleLogin(call, resolvedLtiConfig!!, resolvedAppBaseUrl!!)
                }
                post("/lti/login") {
                    LtiLoginHandler.handleLogin(call, resolvedLtiConfig!!, resolvedAppBaseUrl!!)
                }
            }

            post("/lti/launch") {
                LtiLaunchHandler.handleLaunch(call, resolvedLtiVerifier!!, resolvedDirectoryDatabase!!)
            }

            post("/api/auth/logout") {
                call.sessions.clear<UserSession>()
                call.sessions.clear<StaffSession>()
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            }

            get("/api/staff/students") {
                call.resolveStaffStudentId() ?: return@get
                WebStaffHandler.handleListStudents(call, resolvedDirectoryDatabase!!, resolvedDbFactory!!)
            }

            post("/api/staff/students/{id}/reset-session") {
                call.resolveStaffStudentId() ?: return@post
                val targetStudentId = call.parameters["id"] ?: ""
                if (!studentIdPattern.matches(targetStudentId)) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid studentId"))
                    return@post
                }
                WebStaffHandler.handleResetSession(call, resolvedDirectoryDatabase!!, targetStudentId)
            }

            get("/api/auth/google/start") {
                val studentId = call.resolveStudentId(resolvedDirectoryDatabase!!) ?: return@get
                GoogleWebOAuthHandler.handleStart(call, studentId, resolvedGoogleWebOAuthService)
            }

            get("/api/auth/google/callback") {
                val studentId = call.resolveStudentId(resolvedDirectoryDatabase!!) ?: return@get
                val container = containerFactory(studentId)
                GoogleWebOAuthHandler.handleCallback(call, studentId, resolvedGoogleWebOAuthService, container)
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