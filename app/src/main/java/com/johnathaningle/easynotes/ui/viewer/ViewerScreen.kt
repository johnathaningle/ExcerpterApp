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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.johnathaningle.easynotes.data.model.Annotation

private data class RenderedBitmapBounds(
    val offsetX: Float,
    val offsetY: Float,
    val renderedWidth: Float,
    val renderedHeight: Float
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    pdfUri: String,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showHighlightsDialog by remember { mutableStateOf(false) }
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

    if (showHighlightsDialog) {
        HighlightsDialog(
            annotations = state.annotations,
            onDelete = { viewModel.deleteAnnotation(it) },
            onUpdateNote = { annotation, note -> viewModel.updateNote(annotation, note) },
            onNavigateToPage = { page ->
                viewModel.goToPage(page)
                showHighlightsDialog = false
            },
            onDismiss = { showHighlightsDialog = false }
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
                    TextButton(onClick = { showHighlightsDialog = true }) {
                        Text("Highlights")
                    }
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
                var composableSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { composableSize = it }
                ) {
                    val bitmapWidth = bitmap.width.toFloat()
                    val bitmapHeight = bitmap.height.toFloat()

                    val renderedBounds = remember(composableSize, bitmapWidth, bitmapHeight) {
                        if (composableSize.width == 0 || composableSize.height == 0 ||
                            bitmapWidth == 0f || bitmapHeight == 0f
                        ) {
                            null
                        } else {
                            val cw = composableSize.width.toFloat()
                            val ch = composableSize.height.toFloat()
                            val scale = minOf(cw / bitmapWidth, ch / bitmapHeight)
                            val rw = bitmapWidth * scale
                            val rh = bitmapHeight * scale
                            val ox = (cw - rw) / 2f
                            val oy = (ch - rh) / 2f
                            RenderedBitmapBounds(ox, oy, rw, rh)
                        }
                    }

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

                                            if (start != null && end != null && renderedBounds != null) {
                                                val b = renderedBounds
                                                if (b.renderedWidth > 0 && b.renderedHeight > 0) {
                                                    val sx = ((start.x - b.offsetX) / b.renderedWidth).coerceIn(0f, 1f)
                                                    val sy = ((start.y - b.offsetY) / b.renderedHeight).coerceIn(0f, 1f)
                                                    val ex = ((end.x - b.offsetX) / b.renderedWidth).coerceIn(0f, 1f)
                                                    val ey = ((end.y - b.offsetY) / b.renderedHeight).coerceIn(0f, 1f)
                                                    viewModel.addAnnotation(
                                                        Annotation(
                                                            pdfUri = pdfUri,
                                                            pageNumber = state.currentPage,
                                                            startX = sx,
                                                            startY = sy,
                                                            endX = ex,
                                                            endY = ey,
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
                    renderedBounds?.let { b ->
                        AnnotationOverlay(
                            annotations = pageAnnotations,
                            bitmapOffsetX = b.offsetX,
                            bitmapOffsetY = b.offsetY,
                            bitmapRenderedWidth = b.renderedWidth,
                            bitmapRenderedHeight = b.renderedHeight
                        )
                    }

                    // Highlight preview while dragging
                    longPressStart?.let { start ->
                        longPressEnd?.let { end ->
                            renderedBounds?.let { b ->
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val color = androidx.compose.ui.graphics.Color(state.selectedColor).copy(alpha = 0.4f)
                                    val clippedStartX = start.x.coerceIn(b.offsetX, b.offsetX + b.renderedWidth)
                                    val clippedStartY = start.y.coerceIn(b.offsetY, b.offsetY + b.renderedHeight)
                                    val clippedEndX = end.x.coerceIn(b.offsetX, b.offsetX + b.renderedWidth)
                                    val clippedEndY = end.y.coerceIn(b.offsetY, b.offsetY + b.renderedHeight)
                                    val drawLeft = minOf(clippedStartX, clippedEndX)
                                    val drawTop = minOf(clippedStartY, clippedEndY)
                                    val drawWidth = kotlin.math.abs(clippedEndX - clippedStartX)
                                    val drawHeight = kotlin.math.abs(clippedEndY - clippedStartY)
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(drawLeft, drawTop),
                                        size = Size(drawWidth, drawHeight)
                                    )
                                }
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
                        Icons.AutoMirrored.Filled.ArrowForward,
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
