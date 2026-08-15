package com.johnathaningle.excerpter.ui.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnathaningle.excerpter.data.model.MasterNote
import com.johnathaningle.excerpter.util.LlmService
import com.johnathaningle.excerpter.util.MasterNoteGenerator
import com.johnathaningle.excerpter.util.MasterNoteGenerator.MasterNoteSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterNoteScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val generation = state.masterNoteGeneration
    val note = state.masterNote
    val sections = remember(note?.markdown) {
        note?.markdown?.let { MasterNoteGenerator.parseSections(it) } ?: emptyList()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-generate on first open when there's no cached note yet.
    LaunchedEffect(Unit) {
        val s = viewModel.state.value
        if (s.masterNote == null && !s.masterNoteGeneration.isGenerating &&
            s.masterNoteGeneration.error == null && s.annotations.isNotEmpty() &&
            LlmService.isAvailable()
        ) {
            viewModel.generateMasterNote()
        }
    }

    fun handleSectionLink(section: MasterNoteSection, target: String) {
        val id = target.removePrefix("#sec-").toLongOrNull() ?: return
        if (section.anchorId == id) {
            viewModel.navigateToAnnotation(id)
            onBack()
            return
        }
        val sectionIndex = sections.indexOfFirst { it.anchorId == id }
        if (sectionIndex >= 0) {
            scope.launch { listState.scrollToItem(sectionIndex) }
        } else {
            viewModel.navigateToAnnotation(id)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Master Note", style = MaterialTheme.typography.titleLarge)
                        note?.let {
                            Text(
                                text = "${it.highlightCount} highlights • ${it.sectionCount} sections",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateMasterNote() }, enabled = !generation.isGenerating) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate master note")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                generation.isGenerating -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (generation.currentSection == 0) {
                                "Preparing the AI model…"
                            } else {
                                "Writing section ${generation.currentSection} of ${generation.totalSections}…"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Each section takes a few seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                generation.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = generation.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.generateMasterNote() }) { Text("Retry") }
                    }
                }

                note == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (state.annotations.isEmpty()) {
                                "No highlights yet. Highlight passages first, then generate a master note."
                            } else {
                                "No master note yet. It is built from all your highlights, grouped into AI-written sections with links back to each highlight."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.generateMasterNote() },
                            enabled = state.annotations.isNotEmpty() && LlmService.isAvailable()
                        ) {
                            Text("Generate")
                        }
                        if (!LlmService.isAvailable()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "AI model not loaded — configure it in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Section TOC chips: tap to jump within the note.
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            itemsIndexed(sections) { index, section ->
                                FilterChip(
                                    selected = false,
                                    onClick = { scope.launch { listState.scrollToItem(index) } },
                                    label = {
                                        Text(
                                            section.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(sections) { index, section ->
                                MasterNoteSectionCard(
                                    section = section,
                                    onLink = { target -> handleSectionLink(section, target) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterNoteSectionCard(
    section: MasterNoteSection,
    onLink: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            SelectionContainer {
                MarkdownBody(markdown = section.body, onLink = onLink)
            }
        }
    }
}
