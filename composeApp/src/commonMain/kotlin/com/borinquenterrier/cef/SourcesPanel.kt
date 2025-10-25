package com.borinquenterrier.cef

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SourcesPanel(modifier: Modifier = Modifier) {
    val sourceItems = listOf(
        SourceItem("Syllabus", "CS 101 Syllabus"),
        SourceItem("Calendar", "Fall 2024 Calendar")
    )

    LazyColumn(modifier = modifier) {
        items(sourceItems) { item ->
            SourceItemView(item = item)
        }
    }
}
