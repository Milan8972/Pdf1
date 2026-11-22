package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ToolCategory
import com.example.model.ToolId
import com.example.ui.components.EmptyStateBlock
import com.example.ui.components.ToolTile

@Composable
fun ToolsScreen(
    columnsCount: Int,
    isProUnlocked: Boolean,
    onSelectTool: (ToolId) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val grouped = remember(query) {
        ToolCategory.entries.mapNotNull { cat ->
            val tools = ToolId.entries.filter {
                it.category == cat && (query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.shortDescription.contains(query, ignoreCase = true))
            }
            if (tools.isEmpty()) null else cat to tools
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = "Tools",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search 28 PDF & image tools...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }
                } else null,
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().testTag("tools_search_input")
            )
        }

        if (grouped.isEmpty()) {
            EmptyStateBlock(
                title = "No tools found",
                subtitle = "Try searching for merge, compress, watermark, OCR, or resize."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                grouped.forEach { (category, tools) ->
                    item(key = "header_${category.name}") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(category.tint)
                                )
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            val rows = tools.chunked(columnsCount.coerceAtLeast(3))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                rows.forEach { rowTools ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowTools.forEach { tool ->
                                            ToolTile(
                                                tool = tool,
                                                isProUnlocked = isProUnlocked,
                                                onClick = { onSelectTool(tool) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columnsCount.coerceAtLeast(3) - rowTools.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
