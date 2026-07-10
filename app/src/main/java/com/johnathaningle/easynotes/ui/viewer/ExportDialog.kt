package com.johnathaningle.easynotes.ui.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.johnathaningle.easynotes.EasyNotesApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExportDialog(
    pdfUri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Card(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Export Annotations",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose what to export:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Today's notes
                Button(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            exportAnnotations(
                                context = context,
                                pdfUri = pdfUri,
                                todayOnly = true
                            )
                            isExporting = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting
                ) {
                    Text("Today's notes")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // All notes
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            exportAnnotations(
                                context = context,
                                pdfUri = pdfUri,
                                todayOnly = false
                            )
                            isExporting = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting
                ) {
                    Text("All notes")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting
                ) {
                    Text("Cancel")
                }

                if (isExporting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private suspend fun exportAnnotations(
    context: Context,
    pdfUri: String,
    todayOnly: Boolean
) {
    withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as EasyNotesApp
            val startTime = if (todayOnly) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            } else {
                0L
            }

            val annotations = if (todayOnly) {
                app.repository.getAnnotationsSince(startTime)
            } else {
                app.repository.getAllAnnotationsForPdf(pdfUri)
            }

            if (annotations.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No annotations to export", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            // Create a simple text file with annotation data
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "easynotes_export_${dateFormat.format(Date())}.txt"

            val exportDir = File(context.filesDir, "exports")
            exportDir.mkdirs()

            val exportFile = File(exportDir, fileName)
            exportFile.bufferedWriter().use { writer ->
                writer.write("Easy Notes Export\n")
                writer.write("Generated: ${dateFormat.format(Date())}\n")
                writer.write("PDF: $pdfUri\n")
                writer.write("Total annotations: ${annotations.size}\n")
                writer.write("\n--- Annotations ---\n\n")

                annotations.groupBy { it.pageNumber }.forEach { (page, pageAnnotations) ->
                    writer.write("Page ${page + 1}:\n")
                    pageAnnotations.forEach { ann ->
                        val colorName = when (ann.color) {
                            0xFFFF0000L -> "Red"
                            0xFF00C853L -> "Green"
                            0xFFFFEB3BL -> "Yellow"
                            0xFF2196F3L -> "Blue"
                            0xFF9C27B0L -> "Purple"
                            else -> "Custom"
                        }
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(ann.timestamp))
                        writer.write(
                            "  [$time] Color: $colorName " +
                            "at (${ann.startX}, ${ann.startY}) - (${ann.endX}, ${ann.endY})\n"
                        )
                    }
                    writer.write("\n")
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Exported to: ${exportFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
