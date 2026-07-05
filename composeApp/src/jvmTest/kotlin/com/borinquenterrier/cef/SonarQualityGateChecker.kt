package com.borinquenterrier.cef

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

/**
 * Polls the Sonar Compute Engine task from the analysis the `sonar` task just submitted,
 * then hard-fails the build if composeApp's Quality Gate is not OK.
 * Run via: ./gradlew :composeApp:checkQualityGate (depends on `sonar`, so one command suffices).
 */
object SonarQualityGateChecker {

    // When run as a Gradle JavaExec task, cwd = composeApp/ (the subproject dir).
    private val REPORT_TASK_FILE = File("build/sonar/report-task.txt").absoluteFile
    private val CLIENT: HttpClient = HttpClient.newHttpClient()

    private data class ReportTask(val ceTaskUrl: String)

    @JvmStatic
    fun main(args: Array<String>) {
        val hostUrl = System.getProperty("sonar.host.url")
            ?: error("sonar.host.url system property not set")
        val token = System.getProperty("sonar.token")
            ?: error("sonar.token system property not set — is SONAR_TOKEN in .env?")
        val auth = "Basic " + Base64.getEncoder().encodeToString("$token:".toByteArray())

        val reportTask = readReportTask()
        println("Polling Compute Engine task: ${reportTask.ceTaskUrl}")
        val analysisId = pollCeTask(reportTask.ceTaskUrl, auth)

        val gateStatus = fetchGateStatus(hostUrl, auth, analysisId)
        printConditions(gateStatus)
        if (gateStatus["status"]?.jsonPrimitive?.content != "OK") {
            error("Quality Gate FAILED — see conditions above.")
        }
        println("Quality Gate OK.")
    }

    private fun readReportTask(): ReportTask {
        if (!REPORT_TASK_FILE.exists()) {
            error("${REPORT_TASK_FILE.absolutePath} not found — run ./gradlew :composeApp:sonar first.")
        }
        val ceTaskUrl = REPORT_TASK_FILE.readLines()
            .firstOrNull { it.startsWith("ceTaskUrl=") }
            ?.substringAfter("=")
            ?: error("ceTaskUrl not found in ${REPORT_TASK_FILE.absolutePath}")
        return ReportTask(ceTaskUrl)
    }

    private fun httpGet(url: String, auth: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", auth)
            .GET()
            .build()
        val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "GET $url returned HTTP ${response.statusCode()}: ${response.body()}" }
        return response.body()
    }

    private fun pollCeTask(ceTaskUrl: String, auth: String, timeoutMs: Long = 60_000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val task = Json.parseToJsonElement(httpGet(ceTaskUrl, auth)).jsonObject["task"]!!.jsonObject
            when (val status = task["status"]!!.jsonPrimitive.content) {
                "SUCCESS" -> return task["analysisId"]!!.jsonPrimitive.content
                "FAILED", "CANCELED" -> error("Compute Engine task $status — analysis did not complete.")
                else -> {
                    check(System.currentTimeMillis() < deadline) { "Timed out waiting for Compute Engine task (last status: $status)" }
                    Thread.sleep(1_000)
                }
            }
        }
    }

    private fun fetchGateStatus(hostUrl: String, auth: String, analysisId: String) =
        Json.parseToJsonElement(
            httpGet("$hostUrl/api/qualitygates/project_status?analysisId=$analysisId", auth)
        ).jsonObject["projectStatus"]!!.jsonObject

    private fun printConditions(gateStatus: Map<String, kotlinx.serialization.json.JsonElement>) {
        val conditions = gateStatus["conditions"]?.jsonArray.orEmpty()
        for (c in conditions) {
            val o = c.jsonObject
            println("  ${o["metricKey"]?.jsonPrimitive?.content}: ${o["actualValue"]?.jsonPrimitive?.content} " +
                "(threshold ${o["comparator"]?.jsonPrimitive?.content} ${o["errorThreshold"]?.jsonPrimitive?.content}) " +
                "-> ${o["status"]?.jsonPrimitive?.content}")
        }
    }
}
