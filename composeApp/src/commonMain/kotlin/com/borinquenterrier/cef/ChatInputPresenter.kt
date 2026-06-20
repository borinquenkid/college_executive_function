package com.borinquenterrier.cef

object ChatInputPresenter {

    fun placeholder(useAllSources: Boolean, sourceCount: Int, selectedTitle: String?): String = when {
        useAllSources && sourceCount == 0 -> "Add sources to get started…"
        useAllSources                     -> "Ask anything across all $sourceCount source(s)…"
        selectedTitle != null             -> "Ask about ${selectedTitle.take(20)}…"
        else                              -> "Select a source, or switch to All Sources mode…"
    }

    fun chipLabel(title: String, maxChars: Int = 22): String =
        if (title.length > maxChars) title.take(maxChars) + "…" else title
}
