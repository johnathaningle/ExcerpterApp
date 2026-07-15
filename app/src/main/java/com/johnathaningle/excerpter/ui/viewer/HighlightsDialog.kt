package com.johnathaningle.excerpter.ui.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.johnathaningle.excerpter.data.model.Annotation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HighlightsDialog(
    annotations: List<Annotation>,
    onDelete: (Annotation) -> Unit,
    onEditNote: (Annotation) -> Unit,
    onNavigateToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isAscending by remember { mutableStateOf(true) }

    val filteredAndSortedAnnotations = remember(annotations, searchQuery, isAscending) {
        val query = searchQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            annotations
        } else {
            annotations.filter { annotation ->
                annotation.heading.lowercase().contains(query) ||
                annotation.note.lowercase().contains(query) ||
                annotation.text.lowercase().contains(query) ||
                "page ${annotation.pageNumber + 1}".contains(query)
            }
        }
        if (isAscending) {
            filtered.sortedBy { it.pageNumber }
        } else {
            filtered.sortedByDescending { it.pageNumber }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Highlights",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { isAscending = !isAscending }) {
                        Icon(
                            imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (isAscending) "Sort ascending" else "Sort descending"
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search highlights...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredAndSortedAnnotations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (annotations.isEmpty()) "No highlights yet" else "No matching highlights",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredAndSortedAnnotations, key = { it.id }) { annotation ->
                            val displayText = when {
                                annotation.heading.isNotBlank() -> annotation.heading
                                annotation.note.isNotBlank() -> annotation.note
                                annotation.text.isNotBlank() -> annotation.text
                                else -> "[Empty Text]"
                            }
                            val isNote = annotation.heading.isNotBlank() || annotation.note.isNotBlank()

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = displayText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = if (isNote) {
                                            MaterialTheme.typography.bodyMedium
                                        } else {
                                            MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "Page ${annotation.pageNumber + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Surface(
                                        modifier = Modifier.size(12.dp),
                                        color = Color(annotation.color),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {}
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDelete(annotation) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete highlight",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        onNavigateToPage(annotation.pageNumber)
                                    },
                                    onLongClick = {
                                        onEditNote(annotation)
                                    }
                                )
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
