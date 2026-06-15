package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
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
            val items = loader.loadSources()
            _sourceItemsWrapper.setValue(items)
            selector.autoSelectFirstFrom(items)
        }
    }

    fun addSource(source: SourceItem) {
        val updatedItems = _sourceItemsWrapper.value + source
        _sourceItemsWrapper.setValue(updatedItems)
        selector.autoSelectFirstFrom(updatedItems)
        adder.addSource(source)
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
