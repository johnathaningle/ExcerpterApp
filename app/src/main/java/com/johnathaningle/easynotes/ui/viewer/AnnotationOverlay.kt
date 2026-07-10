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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            val color = Color(annotation.color).copy(alpha = 0.4f)

            val left = minOf(annotation.startX, annotation.endX) * size.width
            val top = minOf(annotation.startY, annotation.endY) * size.height
            val width = kotlin.math.abs(annotation.endX - annotation.startX) * size.width
            val height = kotlin.math.abs(annotation.endY - annotation.startY) * size.height

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height)
            )
        }
    }
}
