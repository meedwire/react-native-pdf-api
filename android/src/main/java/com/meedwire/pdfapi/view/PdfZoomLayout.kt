package com.meedwire.pdfapi.view

import android.content.Context
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import com.meedwire.pdfapi.support.PDF_VIEW_BACKGROUND_COLOR
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class PdfZoomLayout(context: Context) : FrameLayout(context) {
  private var scaleFactor = 1f
  private var maxZoom = 5f
  private var contentTranslationX = 0f
  private var contentTranslationY = 0f
  private var centerContent = false

  val zoomScale: Float
    get() = scaleFactor

  val maximumZoom: Float
    get() = maxZoom

  val verticalContentTranslation: Float
    get() = contentTranslationY

  init {
    clipToPadding = true
    setBackgroundColor(PDF_VIEW_BACKGROUND_COLOR)
  }

  fun setCenterContent(centerContent: Boolean) {
    this.centerContent = centerContent
    requestLayout()
  }

  fun setMaxZoom(maxZoom: Float) {
    this.maxZoom = max(maxZoom, 1f)
    scaleFactor = min(scaleFactor, this.maxZoom)
    requestLayout()
  }

  fun resetZoom() {
    scaleFactor = 1f
    contentTranslationX = 0f
    contentTranslationY = 0f
    getChildAt(0)?.let { child ->
      child.scaleX = 1f
      child.scaleY = 1f
      child.translationX = 0f
      child.translationY = 0f
    }
    requestLayout()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    getChildAt(0)?.let { child ->
      child.layout(0, 0, width, child.measuredHeight)
    }
    applyTransform()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val viewportHeight = MeasureSpec.getSize(heightMeasureSpec)
    val child = getChildAt(0)
    child?.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    )

    val contentHeight = ((child?.measuredHeight ?: 0) * scaleFactor).roundToInt()
    val measuredHeight = if (centerContent) {
      max(viewportHeight, contentHeight)
    } else {
      contentHeight
    }
    setMeasuredDimension(width, measuredHeight)
  }

  fun canPanHorizontally(): Boolean {
    val child = getChildAt(0) ?: return false

    return scaleFactor > 1f && child.width * scaleFactor > width
  }

  fun panHorizontallyBy(deltaX: Float) {
    contentTranslationX += deltaX
    applyTransform()
  }

  fun focusContentX(contentX: Float) {
    if (!canPanHorizontally()) return

    contentTranslationX = width / 2f - contentX * scaleFactor
    applyTransform()
  }

  fun setScale(nextScale: Float, focusX: Float): Float {
    val previousScale = scaleFactor
    val normalizedScale = nextScale.coerceIn(1f, maxZoom)
    if (normalizedScale == previousScale) return scaleFactor

    val contentFocusX = (focusX - contentTranslationX) / previousScale
    scaleFactor = normalizedScale
    contentTranslationX = focusX - contentFocusX * scaleFactor
    requestLayout()

    return scaleFactor
  }

  private fun applyTransform() {
    val child = getChildAt(0) ?: return

    child.scaleX = scaleFactor
    child.scaleY = scaleFactor

    val scaledWidth = child.width * scaleFactor
    val scaledHeight = child.height * scaleFactor
    contentTranslationX = constrainAxis(contentTranslationX, width.toFloat(), scaledWidth, true)
    contentTranslationY = if (centerContent && scaledHeight < height) {
      (height - scaledHeight) / 2f
    } else {
      0f
    }
    child.translationX = contentTranslationX
    child.translationY = contentTranslationY
  }

  private fun constrainAxis(
    translation: Float,
    viewportSize: Float,
    contentSize: Float,
    centerWhenSmaller: Boolean
  ): Float {
    if (contentSize <= viewportSize) {
      return if (centerWhenSmaller) (viewportSize - contentSize) / 2f else 0f
    }

    return translation.coerceIn(viewportSize - contentSize, 0f)
  }
}
