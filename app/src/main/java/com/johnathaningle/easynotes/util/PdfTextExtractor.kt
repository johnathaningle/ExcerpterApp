package com.johnathaningle.easynotes.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

object PdfTextExtractor {

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun extractTextFromRegion(
        context: Context,
        pdfUri: String,
        pageNumber: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): String {
        val uri = Uri.parse(pdfUri)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""

        return try {
            val pdfDoc = PDDocument.load(inputStream)
            val page = pdfDoc.getPage(pageNumber)
            val mediaBox = page.mediaBox
            val pageWidth = mediaBox.width.toDouble()
            val pageHeight = mediaBox.height.toDouble()

            val left = minOf(startX, endX) * pageWidth
            val right = maxOf(startX, endX) * pageWidth
            val top = (1.0 - maxOf(startY, endY)) * pageHeight
            val bottom = (1.0 - minOf(startY, endY)) * pageHeight

            val extractor = RegionTextStripper(left, right, top, bottom)
            extractor.startPage = pageNumber + 1
            extractor.endPage = pageNumber + 1
            val text = extractor.getText(pdfDoc).trim()

            pdfDoc.close()
            text
        } catch (e: Exception) {
            ""
        } finally {
            inputStream.close()
        }
    }
}

private class RegionTextStripper(
    private val left: Double,
    private val right: Double,
    private val top: Double,
    private val bottom: Double
) : PDFTextStripper() {

    private val regionChars = StringBuilder()
    private var lastY = -1.0
    private var lastX = -1.0

    override fun processTextPosition(text: TextPosition) {
        val x = text.xDirAdj.toDouble()
        val y = text.yDirAdj.toDouble()

        if (x in left..right && y in top..bottom) {
            if (lastY != -1.0 && kotlin.math.abs(y - lastY) > text.heightDir * 1.5) {
                regionChars.append('\n')
            } else if (lastX != -1.0 && x - lastX > text.widthDirAdj * 2) {
                regionChars.append(' ')
            }
            regionChars.append(text.unicode)
            lastY = y
            lastX = x + text.widthDirAdj
        }
    }

    override fun getText(doc: PDDocument): String {
        regionChars.clear()
        lastY = -1.0
        lastX = -1.0
        super.getText(doc)
        return regionChars.toString()
    }
}
