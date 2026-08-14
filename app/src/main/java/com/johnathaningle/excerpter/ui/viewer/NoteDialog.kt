package com.johnathaningle.excerpter.ui.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.johnathaningle.excerpter.data.model.Annotation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    annotation: Annotation,
    onDismiss: () -> Unit,
    onSave: (heading: String, note: String) -> Unit,
    onSummarize: suspend (annotation: Annotation) -> String,
    onGenerateHeading: suspend (String) -> String,
    isModelAvailable: Boolean = false,
    modelError: String? = null
) {
    var heading by remember { mutableStateOf(annotation.heading) }
    var note by remember { mutableStateOf(annotation.note.ifBlank { annotation.text }) }
    var showPreview by remember { mutableStateOf(false) }
    var isGeneratingHeading by remember { mutableStateOf(false) }
    var isSummarizing by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Note") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSave(heading, note) }) {
                            Text("Save")
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = heading,
                            onValueChange = { heading = it },
                            placeholder = { Text("Heading") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (note.isNotBlank() && isModelAvailable) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isGeneratingHeading = true
                                        val result = onGenerateHeading(note)
                                        if (result.isNotBlank()) heading = result
                                        isGeneratingHeading = false
                                    }
                                },
                                enabled = !isGeneratingHeading
                            ) {
                                if (isGeneratingHeading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Generate heading",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showPreview = false }) {
                            Text(
                                text = "Edit",
                                color = if (showPreview) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        TextButton(onClick = { showPreview = true }) {
                            Text(
                                text = "Preview",
                                color = if (showPreview) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    if (showPreview && note.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                SelectionContainer {
                                    MarkdownBody(markdown = note, onLink = {})
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text("Note") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            minLines = 5
                        )
                    }

                    if (isModelAvailable) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSummarizing = true
                                    summaryError = null
                                    runCatching { onSummarize(annotation) }
                                        .onSuccess {
                                            note = it
                                            showPreview = true
                                        }
                                        .onFailure { summaryError = it.message }
                                    isSummarizing = false
                                }
                            },
                            enabled = !isSummarizing && annotation.text.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSummarizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Summarize Selection")
                        }
                        if (summaryError != null) {
                            Text(
                                text = summaryError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            text = modelError
                                ?: "AI model not loaded. Add one in Settings → AI Model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
