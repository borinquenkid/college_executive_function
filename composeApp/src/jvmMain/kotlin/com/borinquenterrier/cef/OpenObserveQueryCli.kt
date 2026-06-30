package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import java.util.Base64

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0].isBlank()) {
        System.err.println("Error: No query string provided. Pass -Pquery=\"...\"")
        System.exit(1)
    }

    val queryStr = args[0]
    val days = if (args.size > 1) args[1].toLongOrNull() ?: 7L else 7L
    val type = if (args.size > 2) args[2].lowercase() else "logs"

    // Load .env file in project root
    var envFile = File(".env")
    if (!envFile.exists()) {
        envFile = File("../.env")
    }

    if (!envFile.exists()) {
        System.err.println("Error: .env file not found in project root.")
        System.exit(1)
    }

    val envProps = Properties()
    envFile.inputStream().use { envProps.load(it) }

    val endpoint = System.getenv("CEF_OTLP_ENDPOINT")
        ?: envProps.getProperty("CEF_OTLP_ENDPOINT")
        ?: ""
    val username = System.getenv("OOC_USERNAME")
        ?: envProps.getProperty("OOC_USERNAME")
        ?: ""
    val token = System.getenv("OOC_TOKEN")
        ?: envProps.getProperty("OOC_TOKEN")
        ?: ""

    if (endpoint.isBlank() || username.isBlank() || token.isBlank()) {
        System.err.println("Error: CEF_OTLP_ENDPOINT, OOC_USERNAME, and OOC_TOKEN must be defined in your .env or system environment.")
        System.exit(1)
    }

    val baseEndpoint = endpoint.substringBefore("/v1/traces").substringBefore("/traces")
    val nowUs = System.currentTimeMillis() * 1000L
    val startTimeUs = nowUs - (days * 24L * 3600L * 1000L * 1000L)
    val auth = Base64.getEncoder().encodeToString("$username:$token".toByteArray())

    val client = HttpClient()

    runBlocking {
        try {
            if (type == "traces") {
                // Query traces via GET
                val url = "$baseEndpoint/default/traces/latest"
                println("Querying Traces: $queryStr")
                println("URL: $url")

                val response: HttpResponse = client.get(url) {
                    header(HttpHeaders.Authorization, "Basic $auth")
                    parameter("start_time", startTimeUs)
                    parameter("end_time", nowUs)
                    parameter("from", 0)
                    parameter("size", 100)
                    if (queryStr.isNotBlank() && queryStr != "*") {
                        parameter("filter", queryStr)
                    }
                }
                if (response.status == HttpStatusCode.OK) {
                    println("\n--- TRACES RESPONSE ---")
                    println(response.bodyAsText())
                    println("-----------------------\n")
                } else {
                    System.err.println("Failed to query OpenObserve traces: ${response.status}\n${response.bodyAsText()}")
                    System.exit(1)
                }
            } else {
                // Query logs via SQL POST
                val url = "$baseEndpoint/_search"
                println("Querying Logs (SQL): $queryStr")
                println("URL: $url")

                val escapedQuery = queryStr
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")

                val requestBody = """
                    {
                      "query": {
                        "sql": "$escapedQuery",
                        "start_time": $startTimeUs,
                        "end_time": $nowUs,
                        "from": 0,
                        "size": 100
                      },
                      "search_type": "ui"
                    }
                """.trimIndent()

                val response: HttpResponse = client.post(url) {
                    header(HttpHeaders.Authorization, "Basic $auth")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                if (response.status == HttpStatusCode.OK) {
                    println("\n--- LOGS RESPONSE ---")
                    println(response.bodyAsText())
                    println("---------------------\n")
                } else {
                    System.err.println("Failed to query OpenObserve logs: ${response.status}\n${response.bodyAsText()}")
                    System.exit(1)
                }
            }
        } catch (e: Exception) {
            System.err.println("Network error querying OpenObserve: ${e.message}")
            System.exit(1)
        } finally {
            client.close()
        }
    }
}
