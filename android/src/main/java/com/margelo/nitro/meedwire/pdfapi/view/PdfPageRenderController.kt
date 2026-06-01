package com.margelo.nitro.meedwire.pdfapi.view

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.margelo.nitro.meedwire.pdfapi.document.PdfDocumentHolder
import com.margelo.nitro.meedwire.pdfapi.document.renderPageBitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class PdfPageRenderController(
  private val mainHandler: Handler = Handler(Looper.getMainLooper()),
  private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor()
) {
  private var generation = 0
  private var holder: PdfDocumentHolder? = null
  private var pages = emptyList<PdfPageLayoutInfo>()
  private var pageViewsByIndex = emptyMap<Int, PdfPageImageView>()
  private var maxPageResolution = 2048
  private val renderingPages = mutableSetOf<Int>()
  private val renderedBitmaps = linkedMapOf<Int, Bitmap>()

  val isShutdown: Boolean
    get() = renderExecutor.isShutdown

  fun bind(
    holder: PdfDocumentHolder,
    pages: List<PdfPageLayoutInfo>,
    pageViews: List<PdfPageImageView>,
    maxPageResolution: Int
  ) {
    reset()
    this.holder = holder
    this.pages = pages
    this.maxPageResolution = maxPageResolution
    pageViewsByIndex = pages
      .mapIndexedNotNull { index, page ->
        pageViews.getOrNull(index)?.let { view -> page.pageIndex to view }
      }
      .toMap()
  }

  fun setMaxPageResolution(maxPageResolution: Int) {
    if (this.maxPageResolution == maxPageResolution) return

    this.maxPageResolution = maxPageResolution
    generation += 1
    renderingPages.clear()
    clearRenderedBitmaps()
  }

  fun reset() {
    generation += 1
    clearRenderedBitmaps()
    holder = null
    pages = emptyList()
    pageViewsByIndex = emptyMap()
    renderingPages.clear()
  }

  fun shutdown() {
    reset()
    renderExecutor.shutdownNow()
  }

  fun renderPagesNearViewport(
    contentTop: Float,
    contentBottom: Float,
    prefetchDistance: Float
  ) {
    if (holder == null) return

    val renderTop = contentTop - prefetchDistance
    val renderBottom = contentBottom + prefetchDistance
    val keepTop = contentTop - prefetchDistance * 2
    val keepBottom = contentBottom + prefetchDistance * 2

    recyclePagesOutsideRange(keepTop, keepBottom)
    pages.forEach { page ->
      val view = pageViewsByIndex[page.pageIndex] ?: return@forEach
      if (view.bottom >= renderTop && view.top <= renderBottom) {
        renderPageIfNeeded(page)
      }
    }
  }

  private fun renderPageIfNeeded(page: PdfPageLayoutInfo) {
    val document = holder ?: return
    val pageIndex = page.pageIndex

    if (renderedBitmaps.containsKey(pageIndex) || !renderingPages.add(pageIndex)) {
      return
    }

    val renderGeneration = generation
    val (targetWidth, targetHeight) = page.targetBitmapSize(maxPageResolution)

    renderExecutor.execute {
      val bitmap = runCatching {
        renderPageBitmap(document, pageIndex, targetWidth, targetHeight)
      }.getOrNull()

      mainHandler.post {
        renderingPages.remove(pageIndex)

        if (
          bitmap == null ||
          renderGeneration != generation ||
          holder !== document ||
          !pageViewsByIndex.containsKey(pageIndex)
        ) {
          bitmap?.safeRecycle()
          return@post
        }

        renderedBitmaps.remove(pageIndex)?.safeRecycle()
        renderedBitmaps[pageIndex] = bitmap
        pageViewsByIndex[pageIndex]?.setPageBitmap(bitmap)
      }
    }
  }

  private fun recyclePagesOutsideRange(contentTop: Float, contentBottom: Float) {
    val pageIndexes = renderedBitmaps.keys.toList()

    pageIndexes.forEach { pageIndex ->
      val view = pageViewsByIndex[pageIndex] ?: return@forEach
      if (view.bottom < contentTop || view.top > contentBottom) {
        recyclePage(pageIndex)
      }
    }
  }

  private fun recyclePage(pageIndex: Int) {
    pageViewsByIndex[pageIndex]?.clearPageBitmap()
    renderedBitmaps.remove(pageIndex)?.safeRecycle()
  }

  private fun clearRenderedBitmaps() {
    pageViewsByIndex.values.forEach { view ->
      view.clearPageBitmap()
    }
    renderedBitmaps.values.forEach { bitmap ->
      bitmap.safeRecycle()
    }
    renderedBitmaps.clear()
  }

  private fun Bitmap.safeRecycle() {
    if (!isRecycled) {
      recycle()
    }
  }
}
