package com.margelo.nitro.meedwire.pdfapi

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.facebook.react.uimanager.ThemedReactContext
import com.margelo.nitro.meedwire.pdfapi.document.PdfDocumentHolder
import com.margelo.nitro.meedwire.pdfapi.document.openPdfDocument
import com.margelo.nitro.meedwire.pdfapi.support.PDF_VIEW_BACKGROUND_COLOR
import com.margelo.nitro.meedwire.pdfapi.support.PdfException
import com.margelo.nitro.meedwire.pdfapi.support.parseBackgroundColor
import com.margelo.nitro.meedwire.pdfapi.support.pdfCapabilities
import com.margelo.nitro.meedwire.pdfapi.view.PdfPageImageView
import com.margelo.nitro.meedwire.pdfapi.view.PdfPageLayoutInfo
import com.margelo.nitro.meedwire.pdfapi.view.PdfPageRenderController
import com.margelo.nitro.meedwire.pdfapi.view.PdfScrollView
import com.margelo.nitro.meedwire.pdfapi.view.PdfSearchHighlight
import com.margelo.nitro.meedwire.pdfapi.view.PdfZoomLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HybridPdfView(
  private val context: ThemedReactContext
) : HybridPdfViewSpec() {
  private val rootView = object : FrameLayout(context) {
    override fun onDetachedFromWindow() {
      super.onDetachedFromWindow()
      handleDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      handleAttachedToWindow()
    }
  }
  override val view: View = rootView
  private val zoomLayout = PdfZoomLayout(context)
  private val scrollView = PdfScrollView(context, zoomLayout).apply {
    clipToPadding = true
    isFillViewport = true
  }
  private val pageContainer = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    pivotX = 0f
    pivotY = 0f
    setBackgroundColor(PDF_VIEW_BACKGROUND_COLOR)
  }
  private var renderController = PdfPageRenderController()
  private var viewerBackgroundColorInt = PDF_VIEW_BACKGROUND_COLOR
  private var pageSpacingPx = 16
  private var maxPageResolutionPx = 2048
  private var singlePageMode = false
  private var initialPageIndex = 0
  private var holder: PdfDocumentHolder? = null
  private var pageInfos = emptyList<PdfPageLayoutInfo>()
  private var pageViews = emptyList<PdfPageImageView>()
  private var currentPageIndex = 0
  private var searchHighlightState: PdfSearchHighlight? = null
  private var lastFocusedSearchRequestId = -1
  private var lastViewportWidth = 0
  private var lastViewportHeight = 0
  private var loadGeneration = 0
  private var isLoading = false
  private var shouldLoadSourceAfterUpdate = false
  override var source: String? = null
    set(value) {
      if (value == field) return

      field = value
      shouldLoadSourceAfterUpdate = true
    }

  override var viewerBackgroundColor: String? = null
    set(value) {
      field = value
      updateViewerBackgroundColor(value)
    }

  override var initialPage: Double? = null
    set(value) {
      field = value
      initialPageIndex = max(0, (value ?: 0.0).roundToInt())
    }

  override var pageSpacing: Double? = null
    set(value) {
      field = value
      pageSpacingPx = ((value ?: 16.0) * context.resources.displayMetrics.density).roundToInt()
    }

  override var maxZoom: Double? = null
    set(value) {
      field = value
      zoomLayout.setMaxZoom((value ?: 5.0).toFloat())
    }

  override var maxPageResolution: Double? = null
    set(value) {
      field = value
      maxPageResolutionPx = max(256, (value ?: 2048.0).roundToInt())
      renderController.setMaxPageResolution(maxPageResolutionPx)
      scheduleVisiblePageRendering()
    }

  override var singlePage: Boolean? = null
    set(value) {
      val nextValue = value ?: false
      if (singlePageMode == nextValue) {
        field = value
        return
      }

      field = value
      singlePageMode = nextValue
      if (source != null && holder != null) {
        loadSource()
      }
    }

  override var searchHighlight: PdfViewerSearchHighlight? = null
    set(value) {
      field = value
      searchHighlightState = PdfSearchHighlight.fromNitro(value)
      applySearchHighlight()
      focusSearchHighlightIfNeeded()
    }

  override var onLoad: ((result: PdfOpenDocumentResult) -> Unit)? = null
  override var onPageChange: ((event: PdfPageChangeEvent) -> Unit)? = null
  override var onError: ((event: PdfErrorEvent) -> Unit)? = null

  init {
    PdfApiContext.set(context)
    updateViewerBackgroundColor(null)
    scrollView.onViewportChanged = {
      handleViewportChanged()
    }
    zoomLayout.addView(
      pageContainer,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )
    scrollView.addView(
      zoomLayout,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    )
    rootView.addView(
      scrollView,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
  }

  override fun afterUpdate() {
    super.afterUpdate()

    if (!shouldLoadSourceAfterUpdate) return

    shouldLoadSourceAfterUpdate = false
    loadSource()
  }

  private fun updateViewerBackgroundColor(color: String?) {
    viewerBackgroundColorInt = parseBackgroundColor(color, PDF_VIEW_BACKGROUND_COLOR)
    rootView.setBackgroundColor(viewerBackgroundColorInt)
    applyViewerBackgroundColor()
  }

  private fun loadSource() {
    val currentSource = source
    val generation = ++loadGeneration
    isLoading = currentSource != null
    renderController.reset()
    pageContainer.removeAllViews()
    pageInfos = emptyList()
    pageViews = emptyList()
    lastViewportWidth = 0
    lastViewportHeight = 0
    zoomLayout.resetZoom()
    holder?.close()
    holder = null

    if (currentSource == null) {
      isLoading = false
      return
    }

    Thread {
      try {
        val document = openPdfDocument(context, currentSource)
        val pageCount = document.renderer.pageCount
        val normalizedInitialPage = initialPageIndex.coerceIn(0, pageCount - 1)
        val pageIndexes = if (singlePageMode) {
          listOf(normalizedInitialPage)
        } else {
          (0 until pageCount).toList()
        }
        val shouldCenterSinglePage = pageIndexes.size == 1
        val pages = pageIndexes.map { pageIndex ->
          document.withPage(pageIndex) { page ->
            PdfPageLayoutInfo(
              pageIndex = pageIndex,
              width = page.width,
              height = page.height
            )
          }
        }

        rootView.post {
          if (generation != loadGeneration || currentSource != source) {
            document.close()
            return@post
          }

          isLoading = false
          holder = document
          pageInfos = pages
          currentPageIndex = normalizedInitialPage
          scrollView.isFillViewport = shouldCenterSinglePage
          zoomLayout.setCenterContent(shouldCenterSinglePage)
          pageContainer.gravity = if (shouldCenterSinglePage) {
            Gravity.CENTER
          } else {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
          }
          val nextPageViews = pages.mapIndexed { index, page ->
            val imageView = PdfPageImageView(context).apply {
              setPageSize(page.width, page.height)
              setFitWithinViewport(shouldCenterSinglePage)
              scaleType = ImageView.ScaleType.FIT_CENTER
              setBackgroundColor(Color.WHITE)
            }
            val params = LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
              if (index > 0) topMargin = pageSpacingPx
            }
            pageContainer.addView(imageView, params)
            imageView
          }
          pageViews = nextPageViews
          applySearchHighlight()
          renderController.bind(document, pages, pageViews, maxPageResolutionPx)
          zoomLayout.post {
            updatePageViewportConstraints()
            zoomLayout.resetZoom()
            if (shouldCenterSinglePage) {
              scrollView.scrollTo(0, 0)
              scrollView.post { scrollView.scrollTo(0, 0) }
            } else {
              val initialChildIndex = pages.indexOfFirst { page ->
                page.pageIndex == normalizedInitialPage
              }
              pageContainer.getChildAt(initialChildIndex.coerceAtLeast(0))?.let { child ->
                scrollView.scrollTo(0, child.top)
              }
            }
            scheduleVisiblePageRendering()
            focusSearchHighlightIfNeeded()
          }
          onLoad?.invoke(
            PdfOpenDocumentResult(
              documentId = document.id,
              sourceUri = currentSource,
              pageCount = pageCount.toDouble(),
              capabilities = pdfCapabilities()
            )
          )
          onPageChange?.invoke(
            PdfPageChangeEvent(
              currentPage = normalizedInitialPage.toDouble(),
              pageCount = pageCount.toDouble()
            )
          )
        }
      } catch (error: Throwable) {
        rootView.post {
          if (generation != loadGeneration) return@post

          isLoading = false
          onError?.invoke(
            PdfErrorEvent(
              code = ((error as? PdfException)?.code ?: "ERR_PDF_OPEN"),
              message = error.message ?: "Unable to open PDF document."
            )
          )
        }
      }
    }.start()
  }

  private fun handleDetachedFromWindow() {
    loadGeneration += 1
    isLoading = false
    renderController.shutdown()
    holder?.close()
    holder = null
  }

  private fun handleAttachedToWindow() {
    if (renderController.isShutdown) {
      renderController = PdfPageRenderController()
    }

    if (source != null && holder == null && !isLoading) {
      loadSource()
    }
  }

  private fun handleViewportChanged() {
    updatePageViewportConstraints()
    scheduleVisiblePageRendering()
    dispatchCurrentPageChange()
  }

  private fun scheduleVisiblePageRendering() {
    if (holder == null || pageViews.isEmpty()) return

    scrollView.post {
      if (holder == null || pageViews.isEmpty()) return@post

      val zoomScale = zoomLayout.zoomScale.coerceAtLeast(1f)
      val viewportTop = scrollView.scrollY / zoomScale
      val viewportBottom = (scrollView.scrollY + scrollView.height) / zoomScale
      val prefetchDistance = max(scrollView.height / zoomScale, pageSpacingPx.toFloat())

      renderController.renderPagesNearViewport(
        contentTop = viewportTop,
        contentBottom = viewportBottom,
        prefetchDistance = prefetchDistance
      )
    }
  }

  private fun dispatchCurrentPageChange() {
    if (holder == null || pageInfos.isEmpty() || pageViews.isEmpty()) return

    val zoomScale = zoomLayout.zoomScale.coerceAtLeast(1f)
    val viewportCenter = (scrollView.scrollY + scrollView.height / 2f) / zoomScale
    val currentPage = pageInfos
      .zip(pageViews)
      .firstOrNull { (_, view) ->
        viewportCenter >= view.top && viewportCenter <= view.bottom
      }
      ?.first
      ?.pageIndex
      ?: pageInfos.lastOrNull { page ->
        val view = pageViews.getOrNull(pageInfos.indexOf(page)) ?: return@lastOrNull false
        view.top <= viewportCenter
      }?.pageIndex
      ?: currentPageIndex

    if (currentPage == currentPageIndex) return

    currentPageIndex = currentPage
    onPageChange?.invoke(
      PdfPageChangeEvent(
        currentPage = currentPageIndex.toDouble(),
        pageCount = (holder?.renderer?.pageCount ?: pageInfos.size).toDouble()
      )
    )
  }

  private fun applyViewerBackgroundColor() {
    scrollView.setBackgroundColor(viewerBackgroundColorInt)
    zoomLayout.setBackgroundColor(viewerBackgroundColorInt)
    pageContainer.setBackgroundColor(viewerBackgroundColorInt)
  }

  private fun applySearchHighlight() {
    val highlight = searchHighlightState

    pageInfos.zip(pageViews).forEach { (page, view) ->
      val bounds = if (highlight?.pageIndex == page.pageIndex) {
        highlight.bounds
      } else {
        emptyList()
      }
      view.setSearchBounds(bounds)
    }
  }

  private fun updatePageViewportConstraints() {
    if (pageViews.isEmpty()) return
    if (scrollView.width <= 0 || scrollView.height <= 0) {
      scrollView.post { updatePageViewportConstraints() }
      return
    }

    val viewportWidth = scrollView.width
    val viewportHeight = scrollView.height
    val viewportChanged = viewportWidth != lastViewportWidth || viewportHeight != lastViewportHeight
    lastViewportWidth = viewportWidth
    lastViewportHeight = viewportHeight

    val shouldFitWithinViewport = pageInfos.size == 1
    var didUpdateLayout = viewportChanged
    pageInfos.zip(pageViews).forEach { (pageInfo, pageView) ->
      pageView.setFitWithinViewport(shouldFitWithinViewport)
      pageView.setViewportSize(viewportWidth, viewportHeight)

      val params = (pageView.layoutParams as? LinearLayout.LayoutParams)
        ?: return@forEach
      val nextWidth: Int
      val nextHeight: Int
      if (shouldFitWithinViewport) {
        val inset = (16 * context.resources.displayMetrics.density).roundToInt()
        val maxWidth = max(1, viewportWidth - inset * 2)
        val maxHeight = max(1, viewportHeight - inset * 2)
        val scale = min(
          maxWidth / pageInfo.width.coerceAtLeast(1).toFloat(),
          maxHeight / pageInfo.height.coerceAtLeast(1).toFloat()
        )
        nextWidth = max(1, (pageInfo.width * scale).roundToInt())
        nextHeight = max(1, (pageInfo.height * scale).roundToInt())
      } else {
        nextWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        nextHeight = ViewGroup.LayoutParams.WRAP_CONTENT
      }
      if (
        params.width != nextWidth ||
        params.height != nextHeight
      ) {
        params.width = nextWidth
        params.height = nextHeight
        pageView.layoutParams = params
        didUpdateLayout = true
      }
    }

    if (didUpdateLayout) {
      pageContainer.requestLayout()
      zoomLayout.requestLayout()
    }
  }

  private fun focusSearchHighlightIfNeeded() {
    val highlight = searchHighlightState ?: return
    if (highlight.pageIndex == null || highlight.requestId == lastFocusedSearchRequestId) {
      return
    }

    val pagePosition = pageInfos.indexOfFirst { page ->
      page.pageIndex == highlight.pageIndex
    }
    if (pagePosition < 0 || pagePosition >= pageViews.size) return

    lastFocusedSearchRequestId = highlight.requestId
    scrollView.post {
      focusSearchHighlight(pagePosition, highlight)
    }
  }

  private fun focusSearchHighlight(pagePosition: Int, highlight: PdfSearchHighlight) {
    val page = pageInfos.getOrNull(pagePosition) ?: return
    val pageView = pageViews.getOrNull(pagePosition) ?: return
    val targetBounds = highlight.focusBounds.firstOrNull() ?: highlight.bounds.firstOrNull()
    val contentCenterX: Float
    val contentCenterY: Float
    if (targetBounds == null) {
      contentCenterX = pageView.left + pageView.width / 2f
      contentCenterY = pageView.top + pageView.height / 2f
    } else {
      val pageX = (targetBounds.x + targetBounds.width / 2f) / page.width.coerceAtLeast(1)
      val pageY = (targetBounds.y + targetBounds.height / 2f) / page.height.coerceAtLeast(1)
      contentCenterX = pageView.left + pageX * pageView.width
      contentCenterY = pageView.top + pageY * pageView.height
    }
    val zoomScale = zoomLayout.zoomScale.coerceAtLeast(1f)
    val maxScrollY = max(0, zoomLayout.height - scrollView.height)
    val targetScrollY = (
      contentCenterY * zoomScale +
        zoomLayout.verticalContentTranslation -
        scrollView.height / 2f
      )
      .roundToInt()
      .coerceIn(0, maxScrollY)

    zoomLayout.focusContentX(contentCenterX)
    scrollView.smoothScrollTo(0, targetScrollY)
    scheduleVisiblePageRendering()
    dispatchCurrentPageChange()
  }
}
