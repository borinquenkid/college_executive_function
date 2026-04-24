package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Interface for any service that can provide documents/text to be analyzed by the AI.
 * Examples: Google Drive, Local Files, Canvas LMS, URL Downloader.
 */
interface SourceProvider {
    val id: String
    val displayName: String
    val icon: ImageVector

    /**
     * Whether this provider requires a connection (e.g. OAuth) before it can be used.
     */
    fun isAuthorized(): Boolean

    /**
     * A UI component that handles the selection or input of a source.
     * When a source is selected, it should call [onSourceAdded].
     */
    @Composable
    fun SelectorUI(
        onSourceAdded: (SourceItem) -> Unit,
        onDismiss: () -> Unit
    )
}
