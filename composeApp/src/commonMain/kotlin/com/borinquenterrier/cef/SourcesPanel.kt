package com.borinquenterrier.cef

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
    onSourceAdded: (SourceItem) -> Unit,
    providers: List<SourceProvider>
) {
    var activeProviderId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .padding(4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Sources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp), 
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            providers.forEach { provider ->
                AssistChip(
                    onClick = { activeProviderId = provider.id },
                    enabled = provider.isAuthorized(),
                    label = { Text(provider.displayName, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(provider.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
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

    activeProviderId?.let { providerId ->
        val provider = providers.find { it.id == providerId }
        provider?.SelectorUI(
            onSourceAdded = {
                activeProviderId = null
                onSourceAdded(it)
            },
            onDismiss = { activeProviderId = null }
        )
    }
}
