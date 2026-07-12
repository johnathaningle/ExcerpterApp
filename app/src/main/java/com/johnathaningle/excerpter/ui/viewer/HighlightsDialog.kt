package com.johnathaningle.excerpter.ui.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Highlights",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                if (annotations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No highlights yet",
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
                        items(annotations, key = { it.id }) { annotation ->
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
