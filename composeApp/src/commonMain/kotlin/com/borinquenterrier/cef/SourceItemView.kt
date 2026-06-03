package com.borinquenterrier.cef

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SourceItemView(item: SourceItem, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        val categoryText = when (item.category) {
            SourceCategory.SYLLABUS -> "Syllabus"
            SourceCategory.READING_MATERIAL -> "Reading Material"
            SourceCategory.LAB_MANUAL -> "Lab Manual"
            SourceCategory.LECTURE_NOTES -> "Lecture Notes"
            SourceCategory.OTHER -> "Other"
        }
        val partsText = if (item.fragments.isNotEmpty()) {
            val count = item.fragments.size
            if (count == 1) "1 part" else "$count parts"
        } else {
            "No content"
        }
        val summary = "$categoryText • $partsText"
        Text(summary, style = MaterialTheme.typography.bodySmall)
    }
}
