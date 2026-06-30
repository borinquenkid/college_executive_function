package com.borinquenterrier.cef

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Clock

/**
 * KMP-native OTLP/HTTP tracer. Sends spans to any OTLP-compatible collector
 * (OpenObserve, Jaeger, OTEL Collector) using plain Ktor HTTP + JSON.
 * No JVM SDK required — works on iOS, Android, and JVM.
 *
 * Config is baked in at build time from .env via BuildSecrets (same obfuscated
 * intArray pattern used for GOOGLE_CLIENT_SECRET). Never exposed to users.
 */
class HttpOtelTracer(
    private val endpoint: String,
    private val authHeader: String,
    private val serviceName: String,
    private val client: HttpClient = HttpClient()
) : Tracer {

    private val exportScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Carries current trace/span IDs + mutable scope through the coroutine context.
    private class SpanCtx(
        val traceId: String,
        val spanId: String,
        val scope: HttpSpanScope
    ) : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<SpanCtx>
        override val key = Key
    }

    override suspend fun <T> span(
        name: String,
        attributes: Map<String, String>,
        block: suspend SpanScope.() -> T
    ): T {
        val parent = currentCoroutineContext()[SpanCtx.Key]
        val traceId = parent?.traceId ?: randomHex(16)
        val spanId = randomHex(8)
        val parentSpanId = parent?.spanId
        val startNs = nowNanos()
        val scope = HttpSpanScope()
        attributes.forEach { (k, v) -> scope.setAttribute(k, v) }

        return try {
            val result = withContext(SpanCtx(traceId, spanId, scope)) { scope.block() }
            exportScope.launch {
                export(traceId, spanId, parentSpanId, name, startNs, nowNanos(), scope, 0, null)
            }
            result
        } catch (e: Exception) {
            exportScope.launch {
                export(traceId, spanId, parentSpanId, name, startNs, nowNanos(), scope, 2, e)
            }
            throw e
        }
    }

    override fun event(name: String, attributes: Map<String, String>) {
        // Best-effort: attach to the current span if we're inside one.
        // event() is non-suspend so we can't use currentCoroutineContext() here;
        // standalone events (outside a span) are silently dropped.
    }

    override fun shutdown() {
        exportScope.cancel()
        client.close()
    }

    private suspend fun export(
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        name: String,
        startNs: Long,
        endNs: Long,
        scope: HttpSpanScope,
        statusCode: Int,
        error: Throwable?
    ) {
        try {
            val json = buildJson(traceId, spanId, parentSpanId, name, startNs, endNs, scope, statusCode, error)
            client.post(endpoint) {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        } catch (_: Exception) {
            // Never let export failures surface to the app.
        }
    }

    private fun buildJson(
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        name: String,
        startNs: Long,
        endNs: Long,
        scope: HttpSpanScope,
        statusCode: Int,
        error: Throwable?
    ): String {
        val strAttrs = scope.stringAttrs.entries.joinToString(",") { (k, v) ->
            """{"key":"${k.esc()}","value":{"stringValue":"${v.esc()}"}}"""
        }
        val intAttrs = scope.longAttrs.entries.joinToString(",") { (k, v) ->
            """{"key":"${k.esc()}","value":{"intValue":"$v"}}"""
        }
        val allAttrs = listOfNotNull(strAttrs.ifEmpty { null }, intAttrs.ifEmpty { null }).joinToString(",")

        val allEvents = scope.events.toMutableList()
        if (error != null) {
            allEvents += Triple("exception", nowNanos(), mapOf(
                "exception.type" to (error::class.simpleName ?: "Exception"),
                "exception.message" to (error.message ?: "")
            ))
        }
        val eventsJson = allEvents.joinToString(",") { (evtName, evtNs, evtAttrs) ->
            val ea = evtAttrs.entries.joinToString(",") { (k, v) ->
                """{"key":"${k.esc()}","value":{"stringValue":"${v.esc()}"}}"""
            }
            """{"name":"${evtName.esc()}","timeUnixNano":"$evtNs","attributes":[$ea]}"""
        }

        val parentField = if (parentSpanId != null) """"parentSpanId":"$parentSpanId",""" else ""

        return buildString {
            append("""{"resourceSpans":[{"resource":{"attributes":[""")
            append("""{"key":"service.name","value":{"stringValue":"$serviceName"}}""")
            append("""]},"scopeSpans":[{"scope":{"name":"com.borinquenterrier.cef"},"spans":[{""")
            append(""""traceId":"$traceId","spanId":"$spanId",""")
            append(parentField)
            append(""""name":"${name.esc()}","kind":1,""")
            append(""""startTimeUnixNano":"$startNs","endTimeUnixNano":"$endNs",""")
            append(""""attributes":[$allAttrs],"events":[$eventsJson],""")
            append(""""status":{"code":$statusCode}""")
            append("""}]}]}]}""")
        }
    }

    companion object {
        fun create(serviceName: String): HttpOtelTracer? {
            val endpoint = BuildSecrets.OTLP_ENDPOINT ?: return null
            val user     = BuildSecrets.OTLP_USER     ?: return null
            val password = BuildSecrets.OTLP_PASSWORD ?: return null
            val auth = base64("$user:$password")
            println("[OTEL] Tracing ENABLED → $endpoint ($serviceName)")
            return HttpOtelTracer(endpoint, "Basic $auth", serviceName)
        }
    }
}

private class HttpSpanScope : SpanScope {
    val stringAttrs = mutableMapOf<String, String>()
    val longAttrs   = mutableMapOf<String, Long>()
    val events      = mutableListOf<Triple<String, Long, Map<String, String>>>()

    override fun setAttribute(key: String, value: String) { stringAttrs[key] = value }
    override fun setAttribute(key: String, value: Long)   { longAttrs[key] = value }
    override fun recordException(t: Throwable) {
        events += Triple("exception", nowNanos(), mapOf(
            "exception.type"    to (t::class.simpleName ?: "Exception"),
            "exception.message" to (t.message ?: "")
        ))
    }
    override fun addEvent(name: String, attributes: Map<String, String>) {
        events += Triple(name, nowNanos(), attributes)
    }
}

private fun nowNanos(): Long {
    val now = Clock.System.now()
    return now.epochSeconds * 1_000_000_000L + now.nanosecondsOfSecond
}

private fun randomHex(bytes: Int) = buildString(bytes * 2) {
    repeat(bytes) { append(Random.Default.nextInt(256).toString(16).padStart(2, '0')) }
}

private fun String.esc() = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

private fun base64(input: String): String {
    val src = input.encodeToByteArray()
    val tbl = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    return buildString((src.size + 2) / 3 * 4) {
        var i = 0
        while (i < src.size) {
            val b0 = src[i].toInt() and 0xFF
            val b1 = if (i + 1 < src.size) src[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < src.size) src[i + 2].toInt() and 0xFF else 0
            append(tbl[b0 shr 2])
            append(tbl[((b0 and 3) shl 4) or (b1 shr 4)])
            append(if (i + 1 < src.size) tbl[((b1 and 15) shl 2) or (b2 shr 6)] else '=')
            append(if (i + 2 < src.size) tbl[b2 and 63] else '=')
            i += 3
        }
    }
}
