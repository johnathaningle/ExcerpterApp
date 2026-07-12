package com.johnathaningle.excerpter.ui.viewer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.johnathaningle.excerpter.ExcerpterApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    text = "Export All Annotations",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Export all annotations as Markdown to your Downloads folder?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            exportAnnotations(context, pdfUri)
                            isExporting = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting
                ) {
                    Text("Export")
                }

                Spacer(modifier = Modifier.height(8.dp))

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
    pdfUri: String
) {
    withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as ExcerpterApp
            val annotations = app.repository.getAllAnnotationsForPdf(pdfUri)

            if (annotations.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No annotations to export", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "excerpter_export_${dateFormat.format(Date())}.md"

            val pdfName = android.net.Uri.parse(pdfUri).lastPathSegment ?: "document"
            val contentBuilder = StringBuilder()
            contentBuilder.append("# $pdfName Notes:\n\n")

            annotations.groupBy { it.pageNumber }.forEach { (page, pageAnnotations) ->
                contentBuilder.append("## Page ${page + 1}\n\n")
                pageAnnotations.forEach { ann ->
                    if (ann.heading.isNotBlank()) {
                        contentBuilder.append("**${ann.heading}**\n\n")
                    }
                    if (ann.note.isNotBlank()) {
                        contentBuilder.append("${ann.note}\n\n")
                    }
                }
            }

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(contentBuilder.toString().toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exported to Downloads folder", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: could not create file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
