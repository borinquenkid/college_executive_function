package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

interface Tracer {
    suspend fun <T> span(name: String, attributes: Map<String, String> = emptyMap(), block: suspend SpanScope.() -> T): T
    fun event(name: String, attributes: Map<String, String> = emptyMap())

    /**
     * Synchronous, timeout-bounded exception report for the case span()/event() can't cover:
     * the process may not survive long enough for their fire-and-forget export to ever run.
     */
    fun recordFatal(throwable: Throwable, attributes: Map<String, String> = emptyMap())

    /** Waits (bounded) for exports already in flight to finish, without cancelling them. */
    fun flush(timeoutMillis: Long = 3_000)

    fun shutdown()
}

interface SpanScope {
    fun setAttribute(key: String, value: String)
    fun setAttribute(key: String, value: Long)
    fun recordException(t: Throwable)
    fun addEvent(name: String, attributes: Map<String, String> = emptyMap())
}

object NoopTracer : Tracer {
    @Suppress("UNUSED_PARAMETER")
    override suspend fun <T> span(name: String, attributes: Map<String, String>, block: suspend SpanScope.() -> T): T =
        NoopSpanScope.block()
    @Suppress("UNUSED_PARAMETER")
    override fun event(name: String, attributes: Map<String, String>) = Unit
    @Suppress("UNUSED_PARAMETER")
    override fun recordFatal(throwable: Throwable, attributes: Map<String, String>) = Unit
    override fun flush(timeoutMillis: Long) = Unit
    override fun shutdown() = Unit
}

private object NoopSpanScope : SpanScope {
    @Suppress("UNUSED_PARAMETER") override fun setAttribute(key: String, value: String) = Unit
    @Suppress("UNUSED_PARAMETER") override fun setAttribute(key: String, value: Long) = Unit
    @Suppress("UNUSED_PARAMETER") override fun recordException(t: Throwable) = Unit
    @Suppress("UNUSED_PARAMETER") override fun addEvent(name: String, attributes: Map<String, String>) = Unit
}

object AppTracer {
    var current: Tracer = NoopTracer
}

expect fun createTracer(settings: Settings, appEnv: AppEnv): Tracer
