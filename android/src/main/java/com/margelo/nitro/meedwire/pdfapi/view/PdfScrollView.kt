package com.margelo.nitro.meedwire.pdfapi.view

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.ScrollView
import com.margelo.nitro.meedwire.pdfapi.support.PDF_VIEW_BACKGROUND_COLOR
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class PdfScrollView(
  context: Context,
  private val zoomLayout: PdfZoomLayout
) : ScrollView(context) {
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val gestureDetector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(event: MotionEvent): Boolean {
        return true
      }

      override fun onDoubleTap(event: MotionEvent): Boolean {
        val nextScale = if (zoomLayout.zoomScale > 1.01f) {
          1f
        } else {
          min(2f, zoomLayout.maximumZoom)
        }
        setZoom(nextScale, event.x, event.y)

        return true
      }
    }
  )
  private val scaleGestureDetector = ScaleGestureDetector(
    context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScale(detector: ScaleGestureDetector): Boolean {
        setZoom(zoomLayout.zoomScale * detector.scaleFactor, detector.focusX, detector.focusY)

        return true
      }
    }
  )
  private var downTouchX = 0f
  private var downTouchY = 0f
  private var lastTouchX = 0f
  private var lastTouchY = 0f
  private var isPanningHorizontally = false
  var onViewportChanged: (() -> Unit)? = null

  init {
    setBackgroundColor(PDF_VIEW_BACKGROUND_COLOR)
  }

  override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
    super.onScrollChanged(left, top, oldLeft, oldTop)
    onViewportChanged?.invoke()
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    onViewportChanged?.invoke()
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    gestureDetector.onTouchEvent(event)
    scaleGestureDetector.onTouchEvent(event)

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downTouchX = event.x
        downTouchY = event.y
        lastTouchX = event.x
        lastTouchY = event.y
        isPanningHorizontally = false
      }

      MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
        parent?.requestDisallowInterceptTouchEvent(true)

        return true
      }

      MotionEvent.ACTION_MOVE -> {
        if (scaleGestureDetector.isInProgress || event.pointerCount > 1) {
          parent?.requestDisallowInterceptTouchEvent(true)

          return true
        }

        val totalDeltaX = event.x - downTouchX
        val totalDeltaY = event.y - downTouchY
        if (
          zoomLayout.canPanHorizontally() &&
          (isPanningHorizontally ||
            (abs(totalDeltaX) > touchSlop && abs(totalDeltaX) > abs(totalDeltaY)))
        ) {
          isPanningHorizontally = true
          parent?.requestDisallowInterceptTouchEvent(true)
          zoomLayout.panHorizontallyBy(event.x - lastTouchX)
          lastTouchX = event.x
          lastTouchY = event.y

          return true
        }

        lastTouchX = event.x
        lastTouchY = event.y
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        isPanningHorizontally = false
        parent?.requestDisallowInterceptTouchEvent(false)
      }
    }

    return super.dispatchTouchEvent(event)
  }

  private fun setZoom(nextScale: Float, focusX: Float, focusY: Float) {
    val previousScale = zoomLayout.zoomScale
    val contentFocusY = (scrollY + focusY - zoomLayout.verticalContentTranslation) / previousScale
    val appliedScale = zoomLayout.setScale(nextScale, focusX)

    if (appliedScale == previousScale) return

    zoomLayout.post {
      val maxScrollY = max(0, zoomLayout.height - height)
      val nextScrollY = (
        contentFocusY * zoomLayout.zoomScale +
          zoomLayout.verticalContentTranslation -
          focusY
        )
        .roundToInt()
        .coerceIn(0, maxScrollY)

      scrollTo(0, nextScrollY)
      onViewportChanged?.invoke()
    }
  }
}
