package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

interface Tracer {
    suspend fun <T> span(name: String, attributes: Map<String, String> = emptyMap(), block: suspend SpanScope.() -> T): T
    fun event(name: String, attributes: Map<String, String> = emptyMap())
    fun shutdown()
}

interface SpanScope {
    fun setAttribute(key: String, value: String)
    fun setAttribute(key: String, value: Long)
    fun recordException(t: Throwable)
    fun addEvent(name: String, attributes: Map<String, String> = emptyMap())
}

object NoopTracer : Tracer {
    override suspend fun <T> span(name: String, attributes: Map<String, String>, block: suspend SpanScope.() -> T): T =
        NoopSpanScope.block()
    override fun event(name: String, attributes: Map<String, String>) = Unit
    override fun shutdown() = Unit
}

private object NoopSpanScope : SpanScope {
    override fun setAttribute(key: String, value: String) = Unit
    override fun setAttribute(key: String, value: Long) = Unit
    override fun recordException(t: Throwable) = Unit
    override fun addEvent(name: String, attributes: Map<String, String>) = Unit
}

object AppTracer {
    var current: Tracer = NoopTracer
}

expect fun createTracer(settings: Settings, appEnv: AppEnv): Tracer
