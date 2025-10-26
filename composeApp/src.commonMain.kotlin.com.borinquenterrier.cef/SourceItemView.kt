package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SourceItemView(item: SourceItem) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        Text(item.content, style = MaterialTheme.typography.bodySmall)
    }
}
