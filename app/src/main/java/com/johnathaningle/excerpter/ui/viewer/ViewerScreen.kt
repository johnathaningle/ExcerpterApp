package com.johnathaningle.excerpter.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.johnathaningle.excerpter.data.model.Annotation

// Where the PDF bitmap is drawn on screen, after ContentScale.Fit centers it
// within the composable. Used to convert between screen coords and normalized
// (0..1) annotation coords stored in the database.
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
    // Screen-space start/end of the current highlight drag. Set during drag when
    // scroll-locked, read by the preview Canvas and the annotation-save logic on
    // finger-up. Null when no drag is active.
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
                val swipeThreshold = with(LocalDensity.current) { 100.dp.toPx() }
                var composableSize by remember { mutableStateOf(IntSize.Zero) }
                val currentState by rememberUpdatedState(state)

                val bitmapWidth = bitmap.width.toFloat()
                val bitmapHeight = bitmap.height.toFloat()

                // Calculate where ContentScale.Fit places the bitmap inside the
                // composable. The bitmap is scaled to fit and centered, so there
                // may be padding on the sides. These bounds let us convert
                // screen-space touch positions into normalized (0..1) annotation
                // coordinates relative to the actual bitmap area.
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

                // The content Box holds the PDF image, annotation overlay, and
                // highlight preview. graphicsLayer handles pinch-to-zoom/pan
                // visually; pointerInput handles all touch gestures below.
                //
                // TransformOrigin(0f, 0f) pins the transform to the top-left
                // corner so that scale/offset math stays simple:
                //   screen = layout * scale + offset
                // Pointer events are NOT affected by graphicsLayer — they are
                // always in layout-space coordinates, so no inverse transform
                // is needed when creating highlights.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { composableSize = it }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = currentState.scale
                            scaleY = currentState.scale
                            translationX = currentState.offset.x
                            translationY = currentState.offset.y
                        }
                        // Unified gesture handler. We use awaitPointerEventScope
                        // (not detectTransformGestures) so we can combine pinch-
                        // to-zoom, single-finger pan/page-swipe, and highlight
                        // drawing in one handler.
                        //
                        // Keys: restarting on uri, page, lock, or color change
                        // resets all gesture state cleanly.
                        .pointerInput(pdfUri, state.currentPage, state.isScrollLocked, state.selectedColor) {
                            awaitPointerEventScope {
                                // Track all active touch pointers by ID.
                                val activePointers = mutableMapOf<PointerId, Offset>()
                                // --- Zoom state ---
                                var isZooming = false
                                var zoomStartDistance = 0f   // finger distance at pinch start
                                var zoomStartScale = 1f      // scale when pinch started
                                var zoomStartOffset = Offset.Zero // offset when pinch started
                                var zoomStartCentroid = Offset.Zero // finger midpoint at pinch start
                                // --- Drag / swipe / highlight state ---
                                var isDragging = false
                                var dragStartPos = Offset.Zero   // where the current drag began
                                var lastDragPos = Offset.Zero    // previous frame position (for pan delta)
                                var pastTouchSlop = false        // true once finger moved far enough to count as a gesture

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                                    for (change in event.changes) {
                                        if (change.pressed) {
                                            activePointers[change.id] = change.position
                                        } else {
                                            activePointers.remove(change.id)
                                        }
                                    }

                                    val count = activePointers.size

                                    // Main gesture dispatch based on active finger count.
                                    when {
                                        // Two+ fingers, not locked → pinch-to-zoom + pan.
                                        // Zoom is disabled when scroll-locked (highlight mode).
                                        count >= 2 && !currentState.isScrollLocked -> {
                                            if (!isZooming) {
                                                isZooming = true
                                                isDragging = false
                                                pastTouchSlop = false
                                                val positions = activePointers.values.toList()
                                                zoomStartDistance = (positions[0] - positions[1]).getDistance()
                                                zoomStartScale = currentState.scale
                                                zoomStartOffset = currentState.offset
                                                zoomStartCentroid = (positions[0] + positions[1]) / 2f
                                            }

                                            val positions = activePointers.values.toList()
                                            val currentDistance = (positions[0] - positions[1]).getDistance()
                                            val currentCentroid = (positions[0] + positions[1]) / 2f

                                            // Pinch ratio → new scale, clamped 1x–5x.
                                            val rawZoom = currentDistance / zoomStartDistance
                                            val newScale = (zoomStartScale * rawZoom).coerceIn(1f, 5f)
                                            val actualZoom = if (zoomStartScale > 0f) newScale / zoomStartScale else 1f
                                            // Offset is adjusted so the zoom is anchored to the
                                            // centroid of the two fingers (the midpoint stays fixed).
                                            val pan = currentCentroid - zoomStartCentroid

                                            viewModel.updateTransform(
                                                newScale,
                                                Offset(
                                                    x = zoomStartOffset.x * actualZoom + zoomStartCentroid.x * (1f - actualZoom) + pan.x,
                                                    y = zoomStartOffset.y * actualZoom + zoomStartCentroid.y * (1f - actualZoom) + pan.y
                                                )
                                            )
                                            event.changes.forEach { it.consume() }
                                        }

                                        // Single finger — could be a highlight drag, page swipe, or pan.
                                        count == 1 -> {
                                            val pos = activePointers.values.first()

                                            // Transition from zoom to single-finger: reset drag state
                                            // so the first frame doesn't register as a big swipe.
                                            if (isZooming) {
                                                isZooming = false
                                                isDragging = true
                                                dragStartPos = pos
                                                lastDragPos = pos
                                                pastTouchSlop = false
                                            }

                                            if (!isDragging) {
                                                isDragging = true
                                                dragStartPos = pos
                                                lastDragPos = pos
                                                pastTouchSlop = false
                                            }

                                            // Ignore small movements — wait until the finger has
                                            // moved past the system touch slop before acting.
                                            val totalDrag = (pos - dragStartPos).getDistance()
                                            if (!pastTouchSlop && totalDrag > viewConfiguration.touchSlop) {
                                                pastTouchSlop = true
                                            }

                                            if (pastTouchSlop) {
                                                if (currentState.isScrollLocked) {
                                                    // Highlight mode: record the drag path in
                                                    // screen coords. These are converted to
                                                    // normalized bitmap coords on finger-up.
                                                    longPressStart = dragStartPos
                                                    longPressEnd = pos
                                                } else if (currentState.scale <= 1.01f) {
                                                    // Not locked, at 1x zoom → horizontal swipe
                                                    // changes page. Uses the total drag distance
                                                    // from the start position (not incremental).
                                                    val dx = pos.x - dragStartPos.x
                                                    if (dx > swipeThreshold) {
                                                        viewModel.previousPage()
                                                        isDragging = false
                                                        pastTouchSlop = false
                                                        dragStartPos = pos
                                                        lastDragPos = pos
                                                    } else if (dx < -swipeThreshold) {
                                                        viewModel.nextPage()
                                                        isDragging = false
                                                        pastTouchSlop = false
                                                        dragStartPos = pos
                                                        lastDragPos = pos
                                                    }
                                                } else {
                                                    // Zoomed in (scale > 1), not locked → pan.
                                                    // Incremental delta from last frame.
                                                    val panDelta = pos - lastDragPos
                                                    viewModel.updateTransform(
                                                        currentState.scale,
                                                        currentState.offset + panDelta
                                                    )
                                                }
                                                lastDragPos = pos
                                            }

                                            event.changes.forEach { it.consume() }
                                        }

                                        // Finger lifted — finalize any in-progress gesture.
                                        count == 0 -> {
                                            // If we were highlight-dragging, save the annotation.
                                            // Convert screen coords → normalized bitmap coords
                                            // using renderedBounds (accounts for ContentScale.Fit
                                            // centering and padding).
                                            if (currentState.isScrollLocked && pastTouchSlop &&
                                                longPressStart != null && longPressEnd != null
                                            ) {
                                                val start = longPressStart!!
                                                val end = longPressEnd!!
                                                val b = renderedBounds
                                                if (b != null && b.renderedWidth > 0 && b.renderedHeight > 0) {
                                                    // Normalize: (screen - bitmapOffset) / bitmapSize → 0..1
                                                    val sx = ((start.x - b.offsetX) / b.renderedWidth).coerceIn(0f, 1f)
                                                    val sy = ((start.y - b.offsetY) / b.renderedHeight).coerceIn(0f, 1f)
                                                    val ex = ((end.x - b.offsetX) / b.renderedWidth).coerceIn(0f, 1f)
                                                    val ey = ((end.y - b.offsetY) / b.renderedHeight).coerceIn(0f, 1f)
                                                    // Ignore tiny accidental drags (< 1% of the page).
                                                    if (kotlin.math.abs(ex - sx) > 0.01f && kotlin.math.abs(ey - sy) > 0.01f) {
                                                        viewModel.addAnnotation(
                                                            Annotation(
                                                                pdfUri = pdfUri,
                                                                pageNumber = currentState.currentPage,
                                                                startX = sx,
                                                                startY = sy,
                                                                endX = ex,
                                                                endY = ey,
                                                                color = currentState.selectedColor
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            isZooming = false
                                            isDragging = false
                                            pastTouchSlop = false
                                            longPressStart = null
                                            longPressEnd = null
                                            activePointers.clear()
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "PDF page ${state.currentPage + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
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

                    // Highlight preview: semi-transparent rectangle drawn while
                    // the user drags in highlight mode. Clipped to the bitmap
                    // bounds so it doesn't extend into the padding area.
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

            // Page navigation arrows (hidden when scroll is locked for highlighting)
            if (!state.isScrollLocked && state.currentPage > 0) {
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

            if (!state.isScrollLocked && state.currentPage < state.pageCount - 1) {
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
