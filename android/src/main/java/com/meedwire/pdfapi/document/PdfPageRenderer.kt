package com.meedwire.pdfapi.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.meedwire.pdfapi.support.DEFAULT_MAX_PIXELS
import com.meedwire.pdfapi.support.PdfException
import com.meedwire.pdfapi.support.numberOption
import com.meedwire.pdfapi.support.parseBackgroundColor
import com.meedwire.pdfapi.support.qualityOption
import com.meedwire.pdfapi.support.stringOption
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal fun renderPageToFile(
  holder: PdfDocumentHolder,
  context: Context,
  pageIndex: Int,
  options: Map<String, Any?>?
): Map<String, Any> {
  return holder.withPage(pageIndex) { page ->
    val scale = numberOption(options, "scale", 1.0)
    val requestedWidth = numberOption(options, "width", 0.0)
    val requestedHeight = numberOption(options, "height", 0.0)
    val maxPixels = numberOption(options, "maxPixels", DEFAULT_MAX_PIXELS.toDouble()).toInt()
    val format = stringOption(options, "format", "png")

    var targetWidth = if (requestedWidth > 0) requestedWidth else page.width * scale
    var targetHeight = if (requestedHeight > 0) requestedHeight else page.height * scale

    if (requestedWidth > 0 && requestedHeight <= 0) {
      targetHeight = requestedWidth * page.height / page.width
    }

    if (requestedHeight > 0 && requestedWidth <= 0) {
      targetWidth = requestedHeight * page.width / page.height
    }

    val width = max(1, targetWidth.roundToInt())
    val height = max(1, targetHeight.roundToInt())

    if (width.toLong() * height.toLong() > maxPixels) {
      throw PdfException("ERR_PDF_RENDER_TOO_LARGE", "Requested render size exceeds maxPixels.")
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(parseBackgroundColor(options?.get("backgroundColor") as? String))
    val matrix = Matrix().apply {
      postScale(width / page.width.toFloat(), height / page.height.toFloat())
    }

    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

    val extension = if (format == "jpeg") "jpg" else "png"
    val outputFile = File(
      pdfCacheDirectory(context),
      "${holder.id}-$pageIndex-${UUID.randomUUID()}.$extension"
    )
    val compressFormat = if (format == "jpeg") {
      Bitmap.CompressFormat.JPEG
    } else {
      Bitmap.CompressFormat.PNG
    }

    outputFile.outputStream().use { output ->
      bitmap.compress(compressFormat, qualityOption(options), output)
    }
    bitmap.recycle()

    mapOf(
      "uri" to Uri.fromFile(outputFile).toString(),
      "width" to width,
      "height" to height,
      "scale" to width / page.width.toDouble(),
      "pageIndex" to pageIndex
    )
  }
}

internal fun renderPageBitmap(
  document: PdfDocumentHolder,
  pageIndex: Int,
  width: Int,
  height: Int
): Bitmap {
  return document.withPage(pageIndex) { page ->
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.WHITE)
    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    bitmap
  }
}
