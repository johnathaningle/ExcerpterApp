package com.johnathaningle.excerpter.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object MlKitTextExtractor {

    private const val TAG = "MlKitTextExtractor"

    private var _recognizer: TextRecognizer? = null
    private val recognizer: TextRecognizer
        get() = synchronized(this) {
            if (_recognizer == null) {
                _recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            _recognizer!!
        }

    fun extractTextFromRegion(
        context: Context,
        pdfUri: String,
        pageNumber: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Task<String> {
        return try {
            val uri = Uri.parse(pdfUri)
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return Tasks.forException(Exception("Could not open PDF file"))

            val renderer = PdfRenderer(pfd)

            if (pageNumber < 0 || pageNumber >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return Tasks.forException(Exception("Invalid page number: $pageNumber"))
            }

            val page = renderer.openPage(pageNumber)

            val scale = 2f
            val pageWidth = page.width.toFloat()
            val pageHeight = page.height.toFloat()

            val fullBitmap = Bitmap.createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(
                fullBitmap,
                null,
                android.graphics.Matrix().apply { postScale(scale, scale) },
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            page.close()

            val left = (minOf(startX, endX) * pageWidth * scale).toInt().coerceIn(0, fullBitmap.width)
            val top = (minOf(startY, endY) * pageHeight * scale).toInt().coerceIn(0, fullBitmap.height)
            val right = (maxOf(startX, endX) * pageWidth * scale).toInt().coerceIn(0, fullBitmap.width)
            val bottom = (maxOf(startY, endY) * pageHeight * scale).toInt().coerceIn(0, fullBitmap.height)

            if (right <= left || bottom <= top) {
                fullBitmap.recycle()
                renderer.close()
                pfd.close()
                return Tasks.forException(Exception("Invalid region bounds"))
            }

            val regionBitmap = Bitmap.createBitmap(fullBitmap, left, top, right - left, bottom - top)
            fullBitmap.recycle()
            renderer.close()
            pfd.close()

            val inputImage = InputImage.fromBitmap(regionBitmap, 0)

            recognizer.process(inputImage).continueWith { task: Task<Text> ->
                regionBitmap.recycle()
                if (task.isSuccessful) {
                    val visionText = task.result
                    val cleanedText = visionText.text
                        .replace("\r\n", " ")
                        .replace("\n", " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    Log.d(TAG, "Extracted text: $cleanedText")
                    cleanedText
                } else {
                    Log.e(TAG, "Text recognition failed", task.exception)
                    throw task.exception ?: Exception("Text recognition failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text", e)
            Tasks.forException(e)
        }
    }

    fun close() = synchronized(this) {
        _recognizer?.close()
        _recognizer = null
    }
}