package com.borinquenterrier.cef

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.util.Base64

class OtelTracer(
    endpoint: String,
    authHeader: String,
    serviceName: String = "cef-desktop"
) : Tracer {

    private val sdk: OpenTelemetrySdk
    private val otelTracer: io.opentelemetry.api.trace.Tracer

    init {
        val exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .addHeader("Authorization", authHeader)
            .build()

        val resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), serviceName)
            )
        )

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .setResource(resource)
            .build()

        sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()

        otelTracer = sdk.getTracer("com.borinquenterrier.cef")
    }

    override suspend fun <T> span(name: String, attributes: Map<String, String>, block: suspend SpanScope.() -> T): T {
        val span = otelTracer.spanBuilder(name).startSpan()
        val scope = span.makeCurrent()
        return try {
            val spanScope = OtelSpanScope(span)
            attributes.forEach { (k, v) -> spanScope.setAttribute(k, v) }
            spanScope.block()
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "error")
            throw e
        } finally {
            scope.close()
            span.end()
        }
    }

    override fun event(name: String, attributes: Map<String, String>) {
        val attrs = Attributes.builder()
        attributes.forEach { (k, v) -> attrs.put(AttributeKey.stringKey(k), v) }
        Span.current().addEvent(name, attrs.build())
    }

    override fun shutdown() {
        sdk.shutdown()
    }

    companion object {
        // Reads from AppEnv (system props → OS env → .env).
        // Endpoint example: http://localhost:5080/api/default
        // The SDK appends /v1/traces automatically for OpenObserve OTLP HTTP.
        fun create(): OtelTracer? {
            val endpoint = AppEnv.get("CEF_OTLP_ENDPOINT") ?: return null
            val user = AppEnv.get("CEF_OTLP_USER") ?: return null
            val password = AppEnv.get("CEF_OTLP_PASSWORD") ?: return null
            val authBase64 = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
            return OtelTracer(endpoint, "Basic $authBase64")
        }
    }
}

private class OtelSpanScope(private val span: Span) : SpanScope {
    override fun setAttribute(key: String, value: String) { span.setAttribute(key, value) }
    override fun setAttribute(key: String, value: Long) { span.setAttribute(key, value) }
    override fun recordException(t: Throwable) { span.recordException(t) }
}

actual fun createTracer(settings: com.russhwolf.settings.Settings): Tracer =
    OtelTracer.create() ?: NoopTracer
