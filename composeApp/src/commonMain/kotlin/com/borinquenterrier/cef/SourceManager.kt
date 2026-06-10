package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight facade orchestrating source management operations.
 * Delegates to specialized services:
 * - SourceLoader: Database queries and reconstruction
 * - SourceAdder: AI-driven event generation on source addition
 * - SourceDeleter: Event cleanup and calendar re-sync on deletion
 * - SourceSelector: Selection state and consistency management
 */
class SourceManager(
    private val loader: SourceLoader,
    private val adder: SourceAdder,
    private val deleter: SourceDeleter,
    private val selector: SourceSelector,
    private val scope: CoroutineScope
) {
    private val _sourceItems = MutableStateFlow<List<SourceItem>>(emptyList())
    val sourceItems: StateFlow<List<SourceItem>> = _sourceItems.asStateFlow()

    val selectedSource: StateFlow<SourceItem?> = selector.selectedSource

    fun loadSources() {
        scope.launch {
            val items = loader.loadSources()
            _sourceItems.value = items
            selector.autoSelectFirstFrom(items)
        }
    }

    fun addSource(source: SourceItem) {
        _sourceItems.value = _sourceItems.value + source
        selector.autoSelectFirstFrom(_sourceItems.value)
        adder.addSource(source)
    }

    fun deleteSource(source: SourceItem) {
        deleter.deleteSource(source)
        _sourceItems.value = _sourceItems.value.filter { it.title != source.title }
        selector.clearIfRemovedFrom(_sourceItems.value)
    }

    fun selectSource(source: SourceItem?) {
        selector.selectSource(source)
    }
}
