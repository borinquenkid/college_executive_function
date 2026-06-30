package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight facade orchestrating source management operations.
 * Delegates to specialized services:
 * - SourceLoader: Database queries and reconstruction
 * - SourceAdder: AI-driven event generation on source addition
 * - SourceDeleter: Event cleanup and calendar re-sync on deletion
 * - SourceSelector: Selection state and consistency management
 *
 * Exposes state via StateFlowReader interfaces for testability.
 */
class SourceManager(
    private val loader: SourceLoader,
    private val adder: SourceAdder,
    private val deleter: SourceDeleter,
    private val selector: SourceSelector,
    private val scope: CoroutineScope
) {
    private val _sourceItemsWrapper: MutableStateFlowWrapper<List<SourceItem>> =
        mutableStateFlowWrapper(emptyList())

    val sourceItems: StateFlowReader<List<SourceItem>> = _sourceItemsWrapper

    val selectedSource: StateFlowReader<SourceItem?> = object : StateFlowReader<SourceItem?> {
        override val value: SourceItem? get() = selector.selectedSource.value
        override suspend fun collect(collector: suspend (SourceItem?) -> Unit) {
            selector.selectedSource.collect(collector)
        }
        override fun asStateFlow() = selector.selectedSource
    }

    fun loadSources() {
        scope.launch {
            val loaded = loader.loadSources()
            // Merge rather than replace: startup reload runs asynchronously, so a source the user
            // added in the meantime must not be clobbered. Keep what's in-memory, append persisted
            // sources not already present (by title).
            val current = _sourceItemsWrapper.value
            val merged = current + loaded.filter { l -> current.none { it.title == l.title } }
            _sourceItemsWrapper.setValue(merged)
            selector.autoSelectFirstFrom(merged)
        }
    }

    fun addSource(source: SourceItem, forceRefresh: Boolean = false) {
        val current = _sourceItemsWrapper.value
        if (!current.any { it.title == source.title }) {
            val updatedItems = current + source
            _sourceItemsWrapper.setValue(updatedItems)
            selector.autoSelectFirstFrom(updatedItems)
        }
        adder.addSource(source, forceRefresh)
    }

    fun reanalyzeSource(source: SourceItem) {
        adder.addSource(source, forceRefresh = true)
    }

    fun deleteSource(source: SourceItem) {
        deleter.deleteSource(source)
        val updatedItems = _sourceItemsWrapper.value.filter { it.title != source.title }
        _sourceItemsWrapper.setValue(updatedItems)
        selector.clearIfRemovedFrom(updatedItems)
    }

    fun selectSource(source: SourceItem?) {
        selector.selectSource(source)
    }
}
