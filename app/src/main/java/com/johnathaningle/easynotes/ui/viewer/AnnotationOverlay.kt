package com.johnathaningle.easynotes.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.johnathaningle.easynotes.data.model.Annotation

@Composable
fun AnnotationOverlay(
    annotations: List<Annotation>,
    bitmapOffsetX: Float,
    bitmapOffsetY: Float,
    bitmapRenderedWidth: Float,
    bitmapRenderedHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            val color = Color(annotation.color).copy(alpha = 0.4f)

            val left = bitmapOffsetX + minOf(annotation.startX, annotation.endX) * bitmapRenderedWidth
            val top = bitmapOffsetY + minOf(annotation.startY, annotation.endY) * bitmapRenderedHeight
            val width = kotlin.math.abs(annotation.endX - annotation.startX) * bitmapRenderedWidth
            val height = kotlin.math.abs(annotation.endY - annotation.startY) * bitmapRenderedHeight

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height)
            )
        }
    }
}
