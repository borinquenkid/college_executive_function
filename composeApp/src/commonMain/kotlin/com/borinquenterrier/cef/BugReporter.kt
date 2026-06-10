package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class TelemetryStats(
    val jsonErrors: Int,
    val rateLimitErrors: Int,
    val criticTotalPasses: Int,
    val criticModifiedPasses: Int
)

class BugReporter(
    private val httpClient: HttpClient,
    private val preferencesRepository: PreferencesRepository,
    private val telemetryManager: TelemetryManager,
    private val logger: Logger
) {
    private val tag = "BugReporter"
    private val scope = CoroutineScope(Dispatchers.Default)

    fun reportError(error: Throwable, context: String = "") {
        scope.launch {
            try {
                val prefs = preferencesRepository.getPreferences()
                if (!prefs.shareAnonymousBugReports) {
                    logger.d(tag, "Bug reporting is disabled by user.")
                    return@launch
                }

                val stats = TelemetryStats(
                    jsonErrors = telemetryManager.getJsonErrors(),
                    rateLimitErrors = telemetryManager.getRateLimitErrors(),
                    criticTotalPasses = telemetryManager.getCriticTotal(),
                    criticModifiedPasses = telemetryManager.getCriticModified()
                )

                val errorName = error::class.simpleName ?: "UnknownError"
                val errorMessage = "${error.message ?: ""} (Context: $context)"
                val stackTrace = error.stackTraceToString()
                val platform = platformName

                logger.d(tag, "Sending anonymous bug report for: $errorName")

                httpClient.post("https://api.web3forms.com/submit") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("access_key", BuildSecrets.WEB3FORMS_ACCESS_KEY)
                        put("subject", "CEF Bug Report: $errorName")
                        put(
                            "message", """
                            Error: $errorMessage
                            Platform: $platform
                            Telemetry: JSON Errors: ${stats.jsonErrors}, Rate Limit Errors: ${stats.rateLimitErrors}, Critic Passes: ${stats.criticModifiedPasses}/${stats.criticTotalPasses}
                            
                            Stack Trace:
                            $stackTrace
                        """.trimIndent()
                        )
                    })
                }
                logger.d(tag, "Bug report sent successfully.")
            } catch (e: Exception) {
                logger.e(tag, "Failed to send bug report", e)
            }
        }
    }
}
