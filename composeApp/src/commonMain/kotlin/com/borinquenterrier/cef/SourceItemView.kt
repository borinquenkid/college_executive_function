package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SourceItemView(item: SourceItem) {
    Column {
        Text(item.title)
        Text(item.content)
    }
}
