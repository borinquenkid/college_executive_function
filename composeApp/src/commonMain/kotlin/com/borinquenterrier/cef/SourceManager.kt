package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SourceManager(
    val container: DependencyContainer,
    val scope: CoroutineScope,
    val onEventsAdded: (List<Event>) -> Unit
) {
    private val _sourceItems = MutableStateFlow<List<SourceItem>>(emptyList())
    val sourceItems: StateFlow<List<SourceItem>> = _sourceItems.asStateFlow()

    private val _selectedSource = MutableStateFlow<SourceItem?>(null)
    val selectedSource: StateFlow<SourceItem?> = _selectedSource.asStateFlow()

    fun loadSources() {
        scope.launch {
            try {
                val sources = container.sourceRepository.getAllSources()
                val items = sources.map { entity ->
                    val fragments = container.sourceRepository.getFragmentsForSource(entity.id).map { frag ->
                        SourceFragment(
                            text = frag.text,
                            pageNumber = frag.pageNumber?.toInt(),
                            sectionTitle = frag.sectionTitle,
                            type = SourceType.valueOf(frag.type),
                            metadata = emptyMap()
                        )
                    }
                    SourceItem(
                        title = entity.title,
                        fragments = fragments,
                        category = SourceCategory.valueOf(entity.category)
                    )
                }
                _sourceItems.value = items
                if (_selectedSource.value == null && items.isNotEmpty()) {
                    _selectedSource.value = items.first()
                }
            } catch (e: Exception) {
                container.logger.e("SourceManager", "Failed to load sources from database", e)
            }
        }
    }

    fun addSource(source: SourceItem) {
        _sourceItems.value = _sourceItems.value + source
        if (_selectedSource.value == null) {
            _selectedSource.value = source
        }
        scope.launch {
            if (container.aiService.isConfigured()) {
                try {
                    val allEvents = container.aiService.generateCalendarEvents(source.fragments)
                    onEventsAdded(allEvents)
                    container.contextAgent.analyzeSource(source)
                } catch (e: Exception) {
                    container.logger.e("SourceManager", "Failed to process added source: ${source.title}", e)
                }
            }
        }
    }

    fun deleteSource(source: SourceItem) {
        scope.launch {
            try {
                container.sourceRepository.deleteSource(source.title)

                val existingEvents = container.localRepository.getAllEvents("default")
                existingEvents.forEach { event ->
                    val id = event.id
                    if (id != null && (id.startsWith(source.title) || event.warning?.contains(source.title) == true)) {
                        container.localRepository.hardDeleteEvent(id, "default")
                    }
                }

                _sourceItems.value = _sourceItems.value.filter { it.title != source.title }
                if (_selectedSource.value?.title == source.title) {
                    _selectedSource.value = _sourceItems.value.firstOrNull()
                }

                container.calendarAgent.synchronize("default")
            } catch (e: Exception) {
                container.logger.e("SourceManager", "Failed to delete source: ${source.title}", e)
            }
        }
    }

    fun selectSource(source: SourceItem?) {
        _selectedSource.value = source
    }
}
