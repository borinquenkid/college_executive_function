package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read-only interface for StateFlow observers.
 * Covariant: consumers can observe but never write.
 */
interface StateFlowReader<out T> {
    val value: T
    suspend fun collect(collector: suspend (T) -> Unit)
    fun asStateFlow(): StateFlow<T>
}

/**
 * Write-only interface for StateFlow owners.
 * Contravariant: owners can write but never read (type-safe enforcement).
 */
interface StateFlowWriter<in T> {
    fun setValue(value: T)
}

/**
 * Full StateFlow interface combining read and write.
 * Used internally only; publicly expose as Reader or Writer.
 */
interface MutableStateFlowWrapper<T> : StateFlowReader<T>, StateFlowWriter<T>

/**
 * Default implementation of StateFlowWrapper.
 * Wraps Kotlin's StateFlow to provide reader/writer separation and testability.
 */
internal class StateFlowWrapperImpl<T>(initialValue: T) : MutableStateFlowWrapper<T> {
    private val _flow = MutableStateFlow(initialValue)

    override val value: T
        get() = _flow.value

    override suspend fun collect(collector: suspend (T) -> Unit) {
        _flow.collect(collector)
    }

    override fun asStateFlow(): StateFlow<T> = _flow.asStateFlow()

    override fun setValue(value: T) {
        _flow.value = value
    }
}

/**
 * Factory function for creating wrapped StateFlows.
 */
fun <T> mutableStateFlowWrapper(initialValue: T): MutableStateFlowWrapper<T> =
    StateFlowWrapperImpl(initialValue)
