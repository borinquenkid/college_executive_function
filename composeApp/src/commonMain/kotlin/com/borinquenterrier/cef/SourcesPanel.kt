package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SourcesPanel(
    modifier: Modifier = Modifier,
    sourceItems: List<SourceItem>,
    selectedSource: SourceItem?,
    onSourceSelected: (SourceItem) -> Unit,
    onSourceAdded: (SourceItem) -> Unit
) {
    var showFilePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Button(onClick = { showFilePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = "Add Source")
            Text("Add Source")
        }
        LazyColumn {
            items(sourceItems) { item ->
                SourceItemView(
                    item = item,
                    isSelected = item == selectedSource,
                    onClick = { onSourceSelected(item) }
                )
            }
        }
    }

    FilePicker(show = showFilePicker) { file ->
        showFilePicker = false
        if (file != null) {
            onSourceAdded(SourceItem(file, file))
        }
    }
}
