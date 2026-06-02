package com.meedwire.pdfapi.view

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.meedwire.pdfapi.document.PdfDocumentHolder
import com.meedwire.pdfapi.document.openPdfDocument
import com.meedwire.pdfapi.support.PDF_VIEW_BACKGROUND_COLOR
import com.meedwire.pdfapi.support.PdfException
import com.meedwire.pdfapi.support.parseBackgroundColor
import com.meedwire.pdfapi.support.pdfCapabilities
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fabric-managed PDF viewer. Holds the full scroll/zoom/page-render logic
 * (previously in the Nitro `HybridPdfView`) and emits `onLoad`/`onPageChange`/
 * `onError` through the React event dispatcher.
 */
@Suppress("ViewConstructor")
class PdfContainerView(
  private val reactContext: ThemedReactContext
) : FrameLayout(reactContext) {
  private val zoomLayout = PdfZoomLayout(reactContext)
  private val scrollView = PdfScrollView(reactContext, zoomLayout).apply {
    clipToPadding = true
    isFillViewport = true
  }
  private val pageContainer = LinearLayout(reactContext).apply {
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
  private var source: String? = null

  init {
    setBackgroundColor(viewerBackgroundColorInt)
    scrollView.onViewportChanged = { handleViewportChanged() }
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
    addView(
      scrollView,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
  }

  private val measureAndLayout = Runnable {
    measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
    )
    layout(left, top, right, bottom)
  }

  override fun requestLayout() {
    super.requestLayout()
    // Fabric ignores the internal requestLayout()s raised while our children are
    // added/sized, so we force the measure+layout pass it skips. Rendering is
    // gated on the pages being laid out (see scheduleVisiblePageRendering), so
    // these cheap measure passes don't kick off any render work.
    if (width > 0 && height > 0) post(measureAndLayout)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (width > 0 && height > 0) post(measureAndLayout)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (renderController.isShutdown) {
      renderController = PdfPageRenderController()
    }
    if (source != null && holder == null && !isLoading) {
      loadSource()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    loadGeneration += 1
    isLoading = false
    renderController.shutdown()
    holder?.close()
    holder = null
  }

  // MARK: - Prop setters

  fun setSource(value: String?) {
    val normalized = if (value.isNullOrEmpty()) null else value
    if (normalized == source) return
    source = normalized
    shouldLoadSourceAfterUpdate = true
  }

  fun setViewerBackgroundColor(value: String?) {
    viewerBackgroundColorInt = parseBackgroundColor(value, PDF_VIEW_BACKGROUND_COLOR)
    setBackgroundColor(viewerBackgroundColorInt)
    applyViewerBackgroundColor()
  }

  fun setInitialPage(value: Double) {
    initialPageIndex = max(0, value.roundToInt())
  }

  fun setPageSpacing(value: Double) {
    pageSpacingPx = (value * resources.displayMetrics.density).roundToInt()
  }

  fun setMaxZoom(value: Double) {
    zoomLayout.setMaxZoom(value.toFloat())
  }

  fun setMaxPageResolution(value: Double) {
    maxPageResolutionPx = max(256, value.roundToInt())
    renderController.setMaxPageResolution(maxPageResolutionPx)
    scheduleVisiblePageRendering()
  }

  fun setSinglePage(value: Boolean) {
    if (singlePageMode == value) return
    singlePageMode = value
    if (source != null && holder != null) {
      loadSource()
    }
  }

  fun setSearchHighlight(value: ReadableMap?) {
    searchHighlightState = PdfSearchHighlight.fromReadableMap(value)
    applySearchHighlight()
    focusSearchHighlightIfNeeded()
  }

  fun applyPendingSource() {
    if (!shouldLoadSourceAfterUpdate) return
    shouldLoadSourceAfterUpdate = false
    loadSource()
  }

  // MARK: - Loading

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
        val document = openPdfDocument(reactContext, currentSource)
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
            PdfPageLayoutInfo(pageIndex = pageIndex, width = page.width, height = page.height)
          }
        }

        post {
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
            val imageView = PdfPageImageView(reactContext).apply {
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
            // Fabric's layout timing varies, so instead of fixed delays we
            // watch for the pages to actually get a size and render then. This
            // reliably paints the first frame (otherwise it can stay blank until
            // some later layout/scroll happens).
            post(measureAndLayout)
            ensureFirstRenderWhenLaidOut(generation)
          }
          emitLoad(document.id, currentSource, pageCount)
          emitPageChange(normalizedInitialPage, pageCount)
        }
      } catch (error: Throwable) {
        post {
          if (generation != loadGeneration) return@post
          isLoading = false
          emitError(
            (error as? PdfException)?.code ?: "ERR_PDF_OPEN",
            error.message ?: "Unable to open PDF document."
          )
        }
      }
    }.start()
  }

  private fun handleViewportChanged() {
    updatePageViewportConstraints()
    scheduleVisiblePageRendering()
    dispatchCurrentPageChange()
  }

  /** Pages have real, distinct positions (so we won't render all of them). */
  private fun pagesAreLaidOut(): Boolean {
    val first = pageViews.firstOrNull() ?: return false
    if (first.height <= 0) return false
    return pageViews.size == 1 || pageViews.last().bottom > first.bottom
  }

  /**
   * Renders the first frame as soon as the pages are actually laid out. Fabric's
   * layout timing for an imperatively-built subtree is not deterministic, so we
   * watch global layout passes instead of relying on fixed delays.
   */
  private fun ensureFirstRenderWhenLaidOut(generation: Int) {
    scrollView.viewTreeObserver.addOnGlobalLayoutListener(
      object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
          val ready = pagesAreLaidOut()
          if (generation != loadGeneration || ready) {
            if (scrollView.viewTreeObserver.isAlive) {
              scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
            if (generation == loadGeneration && ready) {
              scheduleVisiblePageRendering()
            }
          }
        }
      }
    )
  }

  private fun scheduleVisiblePageRendering() {
    if (holder == null || pageViews.isEmpty()) return
    // Until the pages have distinct laid-out positions, every page reports
    // top/bottom 0 and the controller would render ALL of them (slow,
    // single-threaded) for a blank result. Wait for a real layout.
    if (!pagesAreLaidOut()) return

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
    emitPageChange(
      currentPageIndex,
      holder?.renderer?.pageCount ?: pageInfos.size
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
    val viewportChanged =
      viewportWidth != lastViewportWidth || viewportHeight != lastViewportHeight
    lastViewportWidth = viewportWidth
    lastViewportHeight = viewportHeight

    val shouldFitWithinViewport = pageInfos.size == 1
    var didUpdateLayout = viewportChanged
    pageInfos.zip(pageViews).forEach { (pageInfo, pageView) ->
      pageView.setFitWithinViewport(shouldFitWithinViewport)
      pageView.setViewportSize(viewportWidth, viewportHeight)

      val params = (pageView.layoutParams as? LinearLayout.LayoutParams) ?: return@forEach
      val nextWidth: Int
      val nextHeight: Int
      if (shouldFitWithinViewport) {
        val inset = (16 * resources.displayMetrics.density).roundToInt()
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
      if (params.width != nextWidth || params.height != nextHeight) {
        params.width = nextWidth
        params.height = nextHeight
        pageView.layoutParams = params
        didUpdateLayout = true
      }
    }

    if (didUpdateLayout) {
      // These bubble up to our requestLayout() override, which forces the
      // measure+layout pass that Fabric otherwise skips for our children.
      pageContainer.requestLayout()
      zoomLayout.requestLayout()
    }
  }

  private fun focusSearchHighlightIfNeeded() {
    val highlight = searchHighlightState ?: return
    if (highlight.pageIndex == null || highlight.requestId == lastFocusedSearchRequestId) {
      return
    }

    val pagePosition = pageInfos.indexOfFirst { page -> page.pageIndex == highlight.pageIndex }
    if (pagePosition < 0 || pagePosition >= pageViews.size) return

    lastFocusedSearchRequestId = highlight.requestId
    scrollView.post { focusSearchHighlight(pagePosition, highlight) }
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

  // MARK: - Events

  private fun emitLoad(documentId: String, sourceUri: String, pageCount: Int) {
    val capabilities = Arguments.createMap()
    pdfCapabilities().forEach { (key, value) -> capabilities.putBoolean(key, value) }
    val payload = Arguments.createMap().apply {
      putString("documentId", documentId)
      putInt("pageCount", pageCount)
      putString("sourceUri", sourceUri)
      putMap("capabilities", capabilities)
    }
    dispatchEvent("topLoad", payload)
  }

  private fun emitPageChange(currentPage: Int, pageCount: Int) {
    val payload = Arguments.createMap().apply {
      putInt("currentPage", currentPage)
      putInt("pageCount", pageCount)
    }
    dispatchEvent("topPageChange", payload)
  }

  private fun emitError(code: String, message: String) {
    val payload = Arguments.createMap().apply {
      putString("code", code)
      putString("message", message)
    }
    dispatchEvent("topError", payload)
  }

  private fun dispatchEvent(eventName: String, payload: WritableMap) {
    val surfaceId = UIManagerHelper.getSurfaceId(this)
    UIManagerHelper
      .getEventDispatcherForReactTag(reactContext, id)
      ?.dispatchEvent(PdfViewEvent(surfaceId, id, eventName, payload))
  }

  private class PdfViewEvent(
    surfaceId: Int,
    viewId: Int,
    private val eventNameValue: String,
    private val payload: WritableMap
  ) : Event<PdfViewEvent>(surfaceId, viewId) {
    override fun getEventName(): String = eventNameValue
    override fun getEventData(): WritableMap = payload
  }
}
