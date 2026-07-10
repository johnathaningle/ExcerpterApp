package com.johnathaningle.easynotes.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ColorOption(
    val color: Long,
    val label: String
)

val highlightColors = listOf(
    ColorOption(0xFFFF0000L, "Red"),
    ColorOption(0xFF00C853L, "Green"),
    ColorOption(0xFFFFEB3BL, "Yellow"),
    ColorOption(0xFF2196F3L, "Blue"),
    ColorOption(0xFF9C27B0L, "Purple"),
    ColorOption(0xFFFF9800L, "Custom")
)

@Composable
fun BottomToolbar(
    selectedColor: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    isScrollLocked: Boolean,
    isHighlightEnabled: Boolean,
    onColorSelected: (Long) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleScrollLock: () -> Unit,
    onToggleHighlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo
            IconButton(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Redo
            IconButton(
                onClick = onRedo,
                enabled = canRedo
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Color dots
            highlightColors.forEach { option ->
                val isSelected = selectedColor == option.color
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(option.color))
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { onColorSelected(option.color) }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Scroll lock
            IconButton(onClick = onToggleScrollLock) {
                Icon(
                    imageVector = if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Toggle scroll lock",
                    tint = if (isScrollLocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Highlight toggle
            IconButton(onClick = onToggleHighlight) {
                Icon(
                    imageVector = Icons.Default.Highlight,
                    contentDescription = "Toggle highlight mode",
                    tint = if (isHighlightEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}
