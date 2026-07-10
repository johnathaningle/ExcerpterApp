package com.johnathaningle.easynotes.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.johnathaningle.easynotes.data.model.Annotation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    pdfUri: String,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var longPressStart by remember { mutableStateOf<Offset?>(null) }
    var longPressEnd by remember { mutableStateOf<Offset?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(pdfUri) {
        viewModel.loadPdf(pdfUri)
    }

    if (showExportDialog) {
        ExportDialog(
            pdfUri = pdfUri,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onColorSelected = { color ->
                viewModel.setSelectedColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showExportDialog = true }) {
                        Text("Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            BottomToolbar(
                selectedColor = state.selectedColor,
                canUndo = state.undoStack.isNotEmpty(),
                canRedo = state.redoStack.isNotEmpty(),
                isScrollLocked = state.isScrollLocked,
                onColorSelected = { viewModel.setSelectedColor(it) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onToggleScrollLock = { viewModel.toggleScrollLock() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.errorMessage?.let { message ->
                Column(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            } ?: state.pageBitmap?.let { bitmap ->
                val imageBitmap = bitmap.asImageBitmap()
                var dragOffsetX by remember { mutableFloatStateOf(0f) }
                val swipeThreshold = with(LocalDensity.current) { 100.dp.toPx() }

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "PDF page ${state.currentPage + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pdfUri, state.currentPage, state.isScrollLocked) {
                                if (!state.isScrollLocked) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { _ -> },
                                        onDragEnd = { },
                                        onDragCancel = { dragOffsetX = 0f },
                                        onHorizontalDrag = { _, dragAmount ->
                                            dragOffsetX += dragAmount
                                            if (dragOffsetX > swipeThreshold) {
                                                viewModel.previousPage()
                                                dragOffsetX = 0f
                                            } else if (dragOffsetX < -swipeThreshold) {
                                                viewModel.nextPage()
                                                dragOffsetX = 0f
                                            }
                                        }
                                    )
                                }
                            }
                            .pointerInput(pdfUri, state.currentPage, state.isScrollLocked, state.selectedColor) {
                                if (state.isScrollLocked) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            longPressStart = offset
                                            longPressEnd = offset
                                        },
                                        onDrag = { change, _ ->
                                            longPressEnd = change.position
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            val start = longPressStart
                                            val end = longPressEnd
                                            longPressStart = null
                                            longPressEnd = null

                                            if (start != null && end != null) {
                                                val w = size.width.toFloat()
                                                val h = size.height.toFloat()
                                                if (w > 0 && h > 0) {
                                                    viewModel.addAnnotation(
                                                        Annotation(
                                                            pdfUri = pdfUri,
                                                            pageNumber = state.currentPage,
                                                            startX = start.x / w,
                                                            startY = start.y / h,
                                                            endX = end.x / w,
                                                            endY = end.y / h,
                                                            color = state.selectedColor
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            longPressStart = null
                                            longPressEnd = null
                                        }
                                    )
                                }
                            },
                        contentScale = ContentScale.Fit
                    )

                    // Annotation overlay
                    val pageAnnotations = state.annotations.filter {
                        it.pageNumber == state.currentPage
                    }
                    AnnotationOverlay(annotations = pageAnnotations)

                    // Highlight preview while dragging
                    longPressStart?.let { start ->
                        longPressEnd?.let { end ->
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val color = androidx.compose.ui.graphics.Color(state.selectedColor).copy(alpha = 0.4f)
                                val left = minOf(start.x, end.x)
                                val top = minOf(start.y, end.y)
                                val width = kotlin.math.abs(end.x - start.x)
                                val height = kotlin.math.abs(end.y - start.y)
                                drawRect(
                                    color = color,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height)
                                )
                            }
                        }
                    }
                }
            }

            // Page indicator
            if (state.pageCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "${state.currentPage + 1} / ${state.pageCount}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Page navigation arrows
            if (state.currentPage > 0) {
                IconButton(
                    onClick = { viewModel.previousPage() },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous page",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            if (state.currentPage < state.pageCount - 1) {
                IconButton(
                    onClick = { viewModel.nextPage() },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Next page",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = listOf(
        0xFFFF0000L to "Red",
        0xFFFF5722L to "Deep Orange",
        0xFFFF9800L to "Orange",
        0xFFFFC107L to "Amber",
        0xFFFFEB3BL to "Yellow",
        0xFFCDDC39L to "Lime",
        0xFF4CAF50L to "Green",
        0xFF00BCD4L to "Cyan",
        0xFF2196F3L to "Blue",
        0xFF3F51B5L to "Indigo",
        0xFF9C27B0L to "Purple",
        0xFFE91E63L to "Pink",
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Choose Color",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(customColors.size) { index ->
                        val (color, _) = customColors[index]
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color(color),
                                    shape = MaterialTheme.shapes.small
                                )
                                .combinedClickable(
                                    onClick = { onColorSelected(color) }
                                )
                        )
                    }
                }
            }
        }
    }
}
