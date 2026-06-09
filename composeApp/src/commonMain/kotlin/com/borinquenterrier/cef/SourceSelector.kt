package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the selected source state and ensures consistency across the list.
 * Auto-selects first source when list is populated, clears when source is removed.
 */
class SourceSelector {
    private val _selectedSource = MutableStateFlow<SourceItem?>(null)
    val selectedSource: StateFlow<SourceItem?> = _selectedSource.asStateFlow()

    fun selectSource(source: SourceItem?) {
        _selectedSource.value = source
    }

    fun autoSelectFirstFrom(items: List<SourceItem>) {
        if (_selectedSource.value == null && items.isNotEmpty()) {
            _selectedSource.value = items.first()
        }
    }

    fun clearIfRemovedFrom(items: List<SourceItem>) {
        if (_selectedSource.value != null && !items.contains(_selectedSource.value)) {
            _selectedSource.value = items.firstOrNull()
        }
    }
}
