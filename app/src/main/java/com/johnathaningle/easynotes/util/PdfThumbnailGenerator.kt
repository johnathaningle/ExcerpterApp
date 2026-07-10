package com.johnathaningle.easynotes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

object PdfThumbnailGenerator {

    private const val THUMBNAIL_WIDTH = 200
    private const val THUMBNAIL_HEIGHT = 280

    fun generate(context: Context, pdfUri: Uri): String? {
        return try {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return null

            val renderer = PdfRenderer(pfd)

            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                return null
            }

            val page = renderer.openPage(0)

            val bitmap = Bitmap.createBitmap(
                THUMBNAIL_WIDTH,
                THUMBNAIL_HEIGHT,
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)

            val scaleX = THUMBNAIL_WIDTH.toFloat() / page.width
            val scaleY = THUMBNAIL_HEIGHT.toFloat() / page.height
            val scale = minOf(scaleX, scaleY)

            val scaledWidth = (page.width * scale).toInt()
            val scaledHeight = (page.height * scale).toInt()

            val matrix = android.graphics.Matrix()
            matrix.setScale(scale, scale)

            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()

            val thumbDir = File(context.filesDir, "thumbnails")
            if (!thumbDir.exists()) thumbDir.mkdirs()

            val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()

            thumbFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(context: Context, thumbnailUri: String) {
        if (thumbnailUri.isNotEmpty()) {
            val file = File(thumbnailUri)
            if (file.exists()) file.delete()
        }
    }
}
