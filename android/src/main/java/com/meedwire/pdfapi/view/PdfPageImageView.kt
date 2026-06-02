package com.meedwire.pdfapi.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View.MeasureSpec
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class PdfPageImageView(context: Context) : ImageView(context) {
  private val density = resources.displayMetrics.density
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(210, 210, 210)
    style = Paint.Style.STROKE
    strokeWidth = 1f
  }
  private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(110, 255, 214, 0)
    style = Paint.Style.FILL
  }
  private val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(210, 255, 172, 0)
    style = Paint.Style.STROKE
    strokeWidth = max(1f, density * 1.5f)
  }
  private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
  private var pageAspectRatio = 1.0
  private var pageWidth = 1f
  private var pageHeight = 1f
  private var pageBitmap: Bitmap? = null
  private var searchBounds = emptyList<PdfSearchBounds>()
  private var fitWithinViewport = false
  private var viewportWidth = 0
  private var viewportHeight = 0

  fun setPageSize(width: Int, height: Int) {
    pageWidth = width.coerceAtLeast(1).toFloat()
    pageHeight = height.coerceAtLeast(1).toFloat()
    pageAspectRatio = height.toDouble() / width.coerceAtLeast(1).toDouble()
    requestLayout()
  }

  fun setPageBitmap(bitmap: Bitmap) {
    pageBitmap = bitmap
    invalidate()
  }

  fun clearPageBitmap() {
    pageBitmap = null
    invalidate()
  }

  fun setSearchBounds(bounds: List<PdfSearchBounds>) {
    searchBounds = bounds
    invalidate()
  }

  fun setFitWithinViewport(fitWithinViewport: Boolean) {
    if (this.fitWithinViewport == fitWithinViewport) return

    this.fitWithinViewport = fitWithinViewport
    requestLayout()
  }

  fun setViewportSize(width: Int, height: Int) {
    if (viewportWidth == width && viewportHeight == height) return

    viewportWidth = width
    viewportHeight = height
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (
      MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY &&
      MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
    ) {
      setMeasuredDimension(
        MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1),
        MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
      )
      return
    }

    val availableWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
    val horizontalInset = (16 * density).roundToInt()
    val verticalInset = (16 * density).roundToInt()
    val maxWidth = if (fitWithinViewport && viewportWidth > 0) {
      max(1, viewportWidth - horizontalInset * 2)
    } else {
      max(1, availableWidth - horizontalInset * 2)
    }
    val naturalWidth = max(1, (pageWidth * density).roundToInt())
    var width = when (MeasureSpec.getMode(widthMeasureSpec)) {
      MeasureSpec.AT_MOST -> min(naturalWidth, maxWidth)
      MeasureSpec.UNSPECIFIED -> naturalWidth
      else -> if (fitWithinViewport) min(availableWidth, maxWidth) else availableWidth
    }

    if (fitWithinViewport && viewportHeight > 0) {
      val maxHeight = max(1, viewportHeight - verticalInset * 2)
      val scale = min(
        maxWidth / pageWidth.coerceAtLeast(1f),
        maxHeight / pageHeight.coerceAtLeast(1f)
      )
      width = min(width, max(1, (pageWidth * scale).roundToInt()))
    }

    val height = max(1, (width * pageAspectRatio).roundToInt())

    setMeasuredDimension(width, height)
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawColor(Color.WHITE)
    pageBitmap?.let { bitmap ->
      canvas.drawBitmap(
        bitmap,
        Rect(0, 0, bitmap.width, bitmap.height),
        RectF(0f, 0f, width.toFloat(), height.toFloat()),
        bitmapPaint
      )
    }
    drawSearchHighlights(canvas)
    canvas.drawRect(0f, 0f, width - 1f, height - 1f, borderPaint)
  }

  private fun drawSearchHighlights(canvas: Canvas) {
    if (searchBounds.isEmpty()) return

    searchBounds.forEach { bound ->
      val left = bound.x / pageWidth * width
      val top = bound.y / pageHeight * height
      val right = (bound.x + bound.width) / pageWidth * width
      val bottom = (bound.y + bound.height) / pageHeight * height

      canvas.drawRect(left, top, right, bottom, highlightPaint)
      canvas.drawRect(left, top, right, bottom, highlightBorderPaint)
    }
  }
}
